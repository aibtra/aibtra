/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.core.WorkingMode
import dev.aibtra.diff.DiffManager
import dev.aibtra.diff.DiffManager.State
import dev.aibtra.gui.DelayedUiRefresher
import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.DialogDisplayer.Companion.create
import dev.aibtra.gui.toolbar.ToolBar
import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.text.Schemes
import dev.aibtra.text.TextNormalizer
import kotlinx.serialization.Serializable
import java.awt.*
import java.awt.event.*
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import kotlin.reflect.KFunction2

class MainFrame(initialWorkingMode: WorkingMode, private val environment: Environment) {
	val dialogDisplayer: DialogDisplayer
	private val frame: JFrame
	private val commandControl: CommandControl
	private val rawTextArea: RawTextArea
	private val refTextArea: RefTextArea
	private val diffManager: DiffManager
	private val profileManager: ProfileManager
	private val requestManager: RequestManager
	private val submitter: Submitter
	private val workFile: WorkFile
	private val submitAction: MainMenuAction
	private val applyChangeAction: MainMenuAction
	private val copyAndCloseAction: MainMenuAction
	private val pasteAndSubmitAction: MainMenuAction
	private val profileComboBox: JComboBox<OpenAIConfiguration.Profile.Name>
	private val schemeComboBox: JComboBox<Schemes.Scheme>
	private val toggleFilterMarkdownAction: MainMenuAction
	private val toggleSelectionMode: ToggleSelectionModeAction
	private val toggleShowDiffBeforeAfterAction: MainMenuAction
	private val toggleDarkModeAction: ToggleDarkModeAction

	private var inScrollPosUpdate = false

	init {
		frame = JFrame(FRAME_TITLE)
		frame.iconImage = Icons.LOGO.getImageIcon(true).image

		dialogDisplayer = create(frame)

		val coroutineDispatcher = environment.coroutineDispatcher
		diffManager = DiffManager(
			{
				TextNormalizer(environment.configurationProvider.get(Schemes).current().textNormalizerConfig).normalize(it)
			}, coroutineDispatcher, environment.debugLog
		)

		profileManager = ProfileManager(initialWorkingMode, environment.configurationProvider)

		rawTextArea = RawTextArea({ text -> diffManager.updateRawText(text, null, profileManager.profile().diffConfig, normalization = DiffManager.Normalization.initialize) }, environment)
		rawTextArea.setWordWrap(profileManager.profile().wordWrap)
		refTextArea = RefTextArea(environment)
		refTextArea.setWordWrap(profileManager.profile().wordWrap)

		workFile = WorkFile(dialogDisplayer)

		val textRefresher = DelayedUiRefresher(100) {
			diffManager.updateRawText(rawTextArea.getText(), rawTextArea.getSelectionRange(), profileManager.profile().diffConfig)
			workFile.setContent(rawTextArea.getText())
		}
		rawTextArea.addContentListener {
			textRefresher.refresh()
		}
		rawTextArea.addSelectionListener { _ ->
			textRefresher.refresh()
		}

		configureScrolling(rawTextArea, DiffManager::updateRawScrollPos)
		configureScrolling(refTextArea, DiffManager::updateRefScrollPos)

		diffManager.addStateListener { state, lastState ->
			Ui.assertEdt()

			val rawText = rawTextArea.getText()
			if (rawText == state.diff.raw) {
				rawTextArea.setDiffCharsAndFilteredText(state.rawChars, state.filtered)
			}

			state.rawText.let {
				if (it.isPart()) {
					rawTextArea.setSelection(IntRange(it.from, it.to))
				}
				else {
					rawTextArea.setSelection(null)
				}
			}

			refTextArea.setText(state.refFormatted, state.refChars)

			Ui.runInEdt {
				if (state.selection && state.diff.refFinished && !lastState.diff.refFinished) {
					rawTextArea.scrollTo(DiffManager.ScrollPos(1, 10))
				}
			}
		}

		diffManager.addScrollListener { raw, ref ->
			Ui.assertEdt()

			rawTextArea.scrollTo(raw)
			refTextArea.scrollTo(ref)
		}

		workFile.addStateListener { state ->
			if (state != null && state.initial) {
				val text = state.content
				rawTextArea.setText(text)
				diffManager.updateRawText(text, null, profileManager.profile().diffConfig, DiffManager.Normalization.stop, null)

				state.initialLine?.let {
					SwingUtilities.invokeLater {
						rawTextArea.scrollToLine(it)
					}
				}
			}

			updateFrameTitle(state?.modified ?: false)
		}

		requestManager = RequestManager(diffManager, coroutineDispatcher, dialogDisplayer)
		requestManager.addProgressListener { inProgress ->
			if (inProgress) {
				frame.rootPane.glassPane.isVisible = true
				frame.rootPane.glassPane.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
			}
			else {
				frame.rootPane.glassPane.isVisible = false
				frame.rootPane.glassPane.cursor = Cursor.getDefaultCursor()
			}
		}

		profileComboBox = createProfileComboBox()
		schemeComboBox = createSchemeComboBox()

		commandControl = createCommandControl()

		submitter = createSubmitter()

		val submitAction = SubmitAction(environment, diffManager, requestManager, submitter)
		this.submitAction = submitAction
		applyChangeAction = ApplyChangeAction(refTextArea, rawTextArea, diffManager, environment.accelerators)
		copyAndCloseAction = CopyAndCloseAction(environment, requestManager, diffManager, rawTextArea, environment.configurationProvider, frame)
		pasteAndSubmitAction = PasteAndSubmitAction(environment, requestManager, profileManager, diffManager, rawTextArea, submitAction)
		toggleSelectionMode = ToggleSelectionModeAction(diffManager, profileManager, rawTextArea, environment.accelerators)
		toggleFilterMarkdownAction = ToggleFilterMarkdownAction(diffManager, profileManager, environment.accelerators)
		toggleShowDiffBeforeAfterAction = ToggleShowRefBeforeAndAfterAction(diffManager, profileManager, environment.accelerators)
		toggleDarkModeAction = ToggleDarkModeAction(environment.theme, environment.configurationProvider, environment.accelerators)

		profileManager.fireInitialization() // to adjust all actions

		configureSubmitOnInvocation()
	}

	fun show() {
		val layout = environment.configurationProvider.get(Layout)
		frame.preferredSize = Dimension(layout.width, layout.height)
		layout.x?.let { x ->
			layout.y?.let { y ->
				frame.location = Point(x, y)
			}
		}
		frame.pack()

		if (layout.maximized) {
			frame.extendedState = Frame.MAXIMIZED_BOTH
		}

		val menubar = JMenuBar()
		fillMenuBar(menubar)
		frame.jMenuBar = menubar

		val mainToolBar = ToolBar(environment.theme, true)
		mainToolBar.add(submitAction)
		mainToolBar.add(profileComboBox)
		mainToolBar.add(Box.createRigidArea(Dimension(5, 0)))
		mainToolBar.add(schemeComboBox)
		mainToolBar.add(toggleSelectionMode)
		mainToolBar.add(Box.createHorizontalGlue())
		mainToolBar.add(applyChangeAction)
		mainToolBar.add(pasteAndSubmitAction)
		mainToolBar.add(toggleShowDiffBeforeAfterAction)
		mainToolBar.add(toggleDarkModeAction)

		val bottomToolBar = ToolBar(environment.theme, false)
		bottomToolBar.add(copyAndCloseAction)
		bottomToolBar.add(Box.createHorizontalGlue())

		val pane = frame.contentPane
		pane.layout = BorderLayout()
		pane.add(mainToolBar.getComponent(), BorderLayout.NORTH)
		pane.add(bottomToolBar.getComponent(), BorderLayout.SOUTH)

		val centerPane = Container()
		centerPane.layout = BorderLayout()
		pane.add(centerPane, BorderLayout.CENTER)

		val rawControl = createRawControl()
		val refControl = createRefControl()
		val splitPane = createSplitPane(rawControl, refControl)

		val mainControl = JPanel()
		mainControl.layout = BorderLayout()
		mainControl.add(commandControl.getComponent(), BorderLayout.NORTH)
		mainControl.add(splitPane, BorderLayout.CENTER)

		centerPane.add(mainControl, BorderLayout.CENTER)

		frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
		frame.isVisible = true

		environment.frameManager.register(this, frame, !environment.systemTrayEnabled)

		frame.addWindowListener(object : WindowAdapter() {
			override fun windowClosing(e: WindowEvent?) {
				checkClose {
					frame.dispose()
				}
			}

			override fun windowClosed(e: WindowEvent?) {
				mainToolBar.dispose()
				bottomToolBar.dispose()
			}
		})
	}

	private fun createRawControl(): Component {
		return rawTextArea.getControl()
	}

	private fun createRefControl(): Component {
		val control = refTextArea.getControl()
		refTextArea.addMouseListener(object : MouseAdapter() {
			override fun mouseReleased(e: MouseEvent) {
				refTextArea.getSelectionRange()?.let {
					val blocks = DiffManager.getSelectedBlocksFromRef(diffManager.state, it)
					if (blocks.isNotEmpty()) {
						val popupMenu = JPopupMenu()
						popupMenu.add(JMenuItem(applyChangeAction))
						popupMenu.show(e.component, e.x, e.y)
					}
				}
			}
		})
		return control
	}

	private fun createSplitPane(rawControl: Component, refControl: Component): JSplitPane {
		val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
		splitPane.resizeWeight = 0.5
		splitPane.topComponent = rawControl
		splitPane.bottomComponent = refControl
		var splitInitializing = true
		splitPane.addComponentListener(object : ComponentAdapter() {
			override fun componentResized(e: ComponentEvent?) {
				if (splitInitializing) {
					splitInitializing = false

					val location = environment.configurationProvider.get(Layout).dividerLocation
					if (location > 0) {
						splitPane.dividerLocation = location
					}
				}
			}
		})
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
			if (!splitInitializing) {
				environment.configurationProvider.change(Layout) {
					it.copy(dividerLocation = splitPane.dividerLocation)
				}
			}
		}
		return splitPane
	}

	fun checkClose(runnable: Runnable) {
		workFile.checkSave(runnable)
	}

	fun closed() {
		val location = frame.location
		val size = frame.size
		environment.configurationProvider.change(Layout) {
			if ((frame.extendedState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
				it.copy(maximized = true)
			}
			else {
				it.copy(x = location.x, y = location.y, width = size.width, height = size.height, maximized = false)
			}
		}

		commandControl.retrieveCommand()
	}

	private fun configureScrolling(textArea: AbstractTextArea<*>, update: KFunction2<DiffManager, DiffManager.ScrollPos, Unit>) {
		textArea.addScrollListener { pos ->
			if (inScrollPosUpdate) {
				return@addScrollListener
			}

			inScrollPosUpdate = true
			try {
				update(diffManager, pos)
			} finally {
				inScrollPosUpdate = false
			}
		}
	}

	private fun createSchemeComboBox(): JComboBox<Schemes.Scheme> {
		val configurationProvider = environment.configurationProvider
		val scheme = configurationProvider.get(Schemes)
		val comboBox = ComboBoxWithPreferredSize(scheme.list.toTypedArray())
		scheme.list.find { it.name == scheme.currentName }?.let {
			comboBox.selectedItem = it
		}

		comboBox.renderer = object : DefaultListCellRenderer() {
			override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
				val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
				if (value is Schemes.Scheme) {
					label.text = value.name
				}
				else {
					label.text = Schemes.DEFAULT_NAME
				}

				return label
			}
		}

		comboBox.adjustWidth()

		comboBox.addItemListener {
			(comboBox.selectedItem as? Schemes.Scheme)?.let { profile ->
				configurationProvider.change(Schemes) {
					it.copy(currentName = profile.name)
				}
				diffManager.updateInitial()?.let {
					rawTextArea.initializeText(it)
				}
			}
		}

		workFile.addStateListener { state ->
			schemeComboBox.isEnabled = state == null
		}

		fun updateEnabledState() {
			(profileComboBox.selectedItem as? OpenAIConfiguration.Profile)?.let { profile ->
				comboBox.isEnabled = profile.supportsSchemes
			}
		}

		profileManager.addListener { _, _ ->
			updateEnabledState()
		}
		updateEnabledState()
		return comboBox
	}

	private fun createProfileComboBox(): JComboBox<OpenAIConfiguration.Profile.Name> {
		val configurationProvider = environment.configurationProvider
		val initialConfiguration = configurationProvider.get(OpenAIConfiguration)
		val comboBox = ComboBoxWithPreferredSize(initialConfiguration.profiles.map { it.name }.toTypedArray())
		comboBox.selectedItem = initialConfiguration.currentProfile(profileManager.workingMode).name

		comboBox.renderer = object : DefaultListCellRenderer() {
			override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
				val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
				if (value is OpenAIConfiguration.Profile.Name) {
					label.text = value.title
				}
				else {
					label.text = "<default instructions>"
				}

				return label
			}
		}

		comboBox.adjustWidth()

		comboBox.addItemListener(object : ItemListener {
			private var lastSelected: OpenAIConfiguration.Profile.Name

			init {
				lastSelected = initialConfiguration.currentProfile(profileManager.workingMode).name
			}

			override fun itemStateChanged(e: ItemEvent?) {
				(comboBox.selectedItem as? OpenAIConfiguration.Profile.Name)?.let { it ->
					profileManager.setProfile(it)
				}
			}
		})

		profileManager.addListener { _, name ->
			comboBox.selectedItem = name
		}

		return comboBox
	}

	private fun createCommandControl(): CommandControl {
		fun updateProfile(commandControl: CommandControl) {
			commandControl.setProfile(profileManager.profile())
		}

		val commandControl = CommandControl(environment.configurationProvider)
		profileManager.addListener { _, _ ->
			updateProfile(commandControl)
		}
		updateProfile(commandControl)
		return commandControl
	}

	private fun createSubmitter(): Submitter {
		val submitter = Submitter(environment, requestManager, commandControl, dialogDisplayer) { profileManager.profile() }
		profileManager.addListener { lastName, name ->
			val profile = profileManager.profile()
			diffManager.setConfig(profile.diffConfig)
			if (profileManager.profile().submitOnProfileChange && lastName != name) {
				submitter.run()
			}
		}
		return submitter
	}

	private fun fillMenuBar(menuBar: JMenuBar) {
		val fileMenu = JMenu("File")
		addAction(fileMenu, OpenAction(workFile, frame, environment))
		addAction(fileMenu, SaveAction(workFile, environment))
		fileMenu.addSeparator()
		addAction(fileMenu, ExitAction(environment))
		menuBar.add(fileMenu)

		val editMenu = JMenu("Edit")
		addAction(editMenu, applyChangeAction)
		editMenu.addSeparator()
		addAction(editMenu, copyAndCloseAction)
		addAction(editMenu, pasteAndSubmitAction)
		editMenu.addSeparator()
		addAction(editMenu, toggleSelectionMode)
		editMenu.addSeparator()
		addAction(editMenu, ToggleHotkeyAction(environment.hotkeyListener, environment.configurationProvider, environment.accelerators, dialogDisplayer))
		addAction(editMenu, TogglePasteOnCloseAction(environment.configurationProvider, environment.accelerators))
		menuBar.add(editMenu)

		val viewMenu = JMenu("View")
		addAction(viewMenu, toggleDarkModeAction)
		if (GuiConfiguration.isSystemTraySupported()) {
			addAction(viewMenu, ToggleSystemTrayAction(environment.configurationProvider, environment.accelerators))
		}
		menuBar.add(viewMenu)

		val onPasteMenu = JMenu("On Paste")
		addAction(onPasteMenu, TextNormalizerAction.createJoinLines(environment.configurationProvider, environment.accelerators))
		onPasteMenu.addSeparator()
		addAction(onPasteMenu, TextNormalizerAction.createChangeDoubleToSingleBlockQuotes(environment.configurationProvider, environment.accelerators))
		addAction(onPasteMenu, TextNormalizerAction.createFixMissingEmptyLineAfterBlockQuote(environment.configurationProvider, environment.accelerators))
		addAction(onPasteMenu, TextNormalizerAction.createRewrapBlockQuotes(environment.configurationProvider, environment.accelerators))

		val profileMenu = JMenu("Profile")
		addAction(profileMenu, toggleFilterMarkdownAction)
		addAction(profileMenu, toggleShowDiffBeforeAfterAction)
		addAction(profileMenu, ToggleWordWrapAction(rawTextArea, refTextArea, profileManager, environment.accelerators))
		profileMenu.addSeparator()
		addAction(profileMenu, ToggleSubmitOnInvocationAction(profileManager, environment.accelerators))
		addAction(profileMenu, ToggleSubmitOnProfileChangeAction(profileManager, environment.accelerators))
		menuBar.add(profileMenu)

		val schemeMenu = JMenu("Scheme")
		schemeMenu.add(onPasteMenu)
		menuBar.add(schemeMenu)

		val helpMenu = JMenu("Help")
		addAction(helpMenu, AcknowledgmentsAction(environment, dialogDisplayer))
		helpMenu.addSeparator()
		addAction(helpMenu, AboutAction(environment, dialogDisplayer))
		menuBar.add(helpMenu)
	}

	fun setText(text: String, workingMode: WorkingMode, profileId: String?) {
		require(workingMode == WorkingMode.open || workingMode == WorkingMode.clipboard)

		updateWorkingMode(workingMode, profileId)

		if (workingMode == WorkingMode.clipboard || rawTextArea.getText().isEmpty()) {
			rawTextArea.initializeText(text)

			environment.paths.getProperty("simulateOutputTextFile")?.let {
				diffManager.updateRefText(Files.readString(Path.of(it)), true)
			}
		}
	}

	fun toFront() {
		frame.toFront()
	}

	private fun configureSubmitOnInvocation() {
		var listener: ((state: State, last: State) -> Unit)? = null
		listener = { state, last ->
			if (state.diff.raw.isNotEmpty() && last.diff.raw.isEmpty()) {
				diffManager.removeStateListener(listener!!)

				if (profileManager.profile().submitOnInvocation &&
					rawTextArea.getText().split("\n", " ", "\t").size >= 2) { // Do not submit single words, this should prevent submitting passwords.
					submitter.run()
				}
			}
		}
		diffManager.addStateListener(listener)
	}

	private fun addAction(menu: JMenu, action: MainMenuAction) {
		menu.add(
			if (action.isSelectable()) {
				JCheckBoxMenuItem(action)
			}
			else {
				JMenuItem(action)
			}
		)
	}

	private fun updateFrameTitle(modified: Boolean) {
		frame.title = (workFile.state?.let {
			it.path.fileName.toString() + (if (modified) "*" else "") + " - "
		} ?: "") + FRAME_TITLE
	}

	fun openFile(fileToOpen: Path, profileId: String?, line: Int?) {
		updateWorkingMode(WorkingMode.file, profileId)
		workFile.load(fileToOpen, line)
	}

	private fun updateWorkingMode(workingMode: WorkingMode, profileId: String?) {
		profileId?.let {
			profileManager.overrideProfile(it)
		}

		profileManager.workingMode = workingMode
		profileComboBox.selectedItem = profileManager.profile().name
	}

	@Serializable
	private data class Layout(val x: Int? = null, val y: Int? = null, val width: Int = DEFAULT_WIDTH, val height: Int = DEFAULT_HEIGHT, val maximized: Boolean = false, val dividerLocation: Int = 0) {
		companion object : ConfigurationFactory<Layout> {
			val DEFAULT_WIDTH: Int
			val DEFAULT_HEIGHT: Int

			init {
				DEFAULT_WIDTH = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 20) 1000 else 1500
				DEFAULT_HEIGHT = DEFAULT_WIDTH * 2 / 3
			}

			override fun name(): String {
				return "frame-layout"
			}

			override fun default(): Layout {
				return Layout()
			}
		}
	}

	private class ComboBoxWithPreferredSize<T>(entries: Array<T>) : JComboBox<T>(entries) {
		private var preferredWidth: Int = 0

		override fun getPreferredSize(): Dimension {
			val superPreferredSize = super.getPreferredSize()
			return Dimension(preferredWidth, superPreferredSize.height)
		}

		override fun getMaximumSize(): Dimension {
			return preferredSize
		}

		fun adjustWidth() {
			var maxWidth = 0
			val jList = JList(model)
			for (i in 0 until itemCount) {
				val comp = renderer.getListCellRendererComponent(jList, getItemAt(i), i, false, false)
				maxWidth = maxOf(comp.preferredSize.width, maxWidth)
			}

			preferredWidth = maxWidth + 30 // have some padding which should be sufficient for every L&F
		}
	}

	companion object {
		const val FRAME_TITLE = "Aibtra 1.0 alpha"
	}
}