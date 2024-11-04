/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.WorkingMode
import dev.aibtra.diff.DiffManager
import dev.aibtra.diff.DiffManager.State
import dev.aibtra.gui.DelayedUiRefresher
import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.toolbar.ToolBar
import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.text.Schemes
import dev.aibtra.text.TextNormalizer
import java.awt.*
import java.awt.event.*
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import kotlin.reflect.KFunction2

internal abstract class MainTab(initialWorkingMode: WorkingMode, private val tabbedPane: MainTabbedPane, private val environment: Environment, val dialogDisplayer: DialogDisplayer) {
	private val commandControl: CommandControl
	protected val rawTextArea: RawTextArea
	private val refTextArea: RefTextArea
	protected val diffManager: DiffManager
	protected val profileManager: ProfileManager
	protected val requestManager: RequestManager
	private val bottomToolBar: ToolBar
	private val submitter: Submitter
	private val mainPanel: JPanel

	val control: Component
	val menuBar: JMenuBar by lazy {
		createMenuBar()
	}
	val toolBar: ToolBar by lazy {
		createToolBar()
	}
	private val submitAction: MainMenuAction
	private val applyChangeAction: MainMenuAction
	protected val profileComboBox: JComboBox<Any>
	private val toggleSelectionMode: ToggleSelectionModeAction
	private val toggleShowDiffBeforeAfterAction: MainMenuAction
	private val toggleDarkModeAction: ToggleDarkModeAction
	private val splitPane: JSplitPane

	private var inScrollPosUpdate = false

	abstract fun getTitle(): String

	init {
		val coroutineDispatcher = environment.coroutineDispatcher
		val mainScope = environment.mainScope
		diffManager = DiffManager(::normalizeText, coroutineDispatcher, mainScope, environment.debugLog)

		profileManager = ProfileManager(initialWorkingMode, environment.configurationProvider)

		rawTextArea = RawTextArea({ text -> diffManager.updateRawText(text, null, profileManager.profile().diffConfig, normalization = DiffManager.Normalization.INITIALIZE) }, environment)
		refTextArea = RefTextArea(environment)

		val textRefresher = DelayedUiRefresher(100) {
			updateContent()
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
					rawTextArea.setSelection(IntRange(it.from, it.to - 1))
				}
				else {
					rawTextArea.setSelection(null)
				}
			}

			refTextArea.setText(state.refFormatted, state.refChars)

			Ui.runInEdt {
				if (state.diff.refFinished && !lastState.diff.refFinished) {
					if (!state.selection) {
						rawTextArea.scrollTo(DiffManager.ScrollPos(1, 10))
					}
					else {
						refTextArea.scrollTo(diffManager.syncRefScrollPos())
					}
				}
			}
		}

		diffManager.addScrollListener { raw, ref ->
			Ui.assertEdt()

			rawTextArea.scrollTo(raw)
			refTextArea.scrollTo(ref)
		}

		profileManager.addListener { _, name ->
			updateWordWrap()
		}

		requestManager = RequestManager(diffManager, coroutineDispatcher, mainScope, dialogDisplayer)

		profileComboBox = createProfileComboBox()

		commandControl = createCommandControl()

		bottomToolBar = ToolBar(environment.theme, false)

		submitter = createSubmitter()

		submitAction = SubmitAction(environment, diffManager, requestManager, submitter)
		applyChangeAction = ApplyChangeAction(refTextArea, rawTextArea, diffManager, environment.accelerators)
		toggleSelectionMode = ToggleSelectionModeAction(diffManager, profileManager, rawTextArea, environment.accelerators)
		toggleShowDiffBeforeAfterAction = ToggleShowRefBeforeAndAfterAction(diffManager, profileManager, environment.accelerators)
		toggleDarkModeAction = ToggleDarkModeAction(environment.theme, environment.configurationProvider, environment.accelerators)

		val rawControl = createRawControl()
		val refControl = createRefControl()
		splitPane = createSplitPane(rawControl, refControl)

		mainPanel = JPanel()
		mainPanel.layout = BorderLayout()
		mainPanel.add(commandControl.getComponent(), BorderLayout.NORTH)
		mainPanel.add(splitPane, BorderLayout.CENTER)

		val overlayPanel = JPanel()
		overlayPanel.background = Color(0, 0, 0, 0)
		overlayPanel.isVisible = false
		overlayPanel.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)

		val tab = JPanel()
		tab.layout = OverlayLayout(tab)
		tab.add(overlayPanel)
		tab.add(mainPanel)

		requestManager.addProgressListener { inProgress ->
			overlayPanel.isVisible = inProgress
		}

		control = tab
	}

	protected fun init() {
		fillBottomToolBar(bottomToolBar)
		if (bottomToolBar.isNotEmpty()) {
			mainPanel.add(bottomToolBar.getComponent(), BorderLayout.SOUTH)
		}

		profileManager.fireInitialization() // to adjust all actions

		environment.paths.getProperty("simulateOutputTextFile")?.let {
			diffManager.updateRefText(Files.readString(Path.of(it)), true)
		}

		configureSubmitOnInvocation()
	}

	protected fun updateTitle() {
		tabbedPane.updateTitle(this)
	}

	internal fun dispose() {
		toolBar.dispose()
		bottomToolBar.dispose()
	}

	protected open fun updateContent() {
		diffManager.updateRawText(rawTextArea.getText(), rawTextArea.getSelectionRange(), profileManager.profile().diffConfig)
	}

	protected open fun normalizeText(raw: String): String {
		return raw
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
						JPopupMenu().apply {
							if (blocks.isNotEmpty()) {
								add(JMenuItem(applyChangeAction))
							}
							if (SwingUtilities.isRightMouseButton(e)) {
								if (componentCount > 0) {
									add(JSeparator())
								}
								add(JMenuItem(CopyRefSelectionAction(refTextArea)))
							}
							else {
								isFocusable = false
							}						
							show(e.component, e.x, e.y)
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
					updateSplitPaneDividerLocation(splitPane)
				}
			}
		})
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
			if (!splitInitializing) {
				environment.configurationProvider.change(MainLayout) {
					it.copy(dividerLocation = splitPane.dividerLocation)
				}
			}
		}

		// To prevent an initial jumping from centered location to stored location in componentResized()
		updateSplitPaneDividerLocation(splitPane)
		return splitPane
	}

	open fun checkClose(runnable: Runnable) {
		runnable.run()
	}

	fun toFront() {
		tabbedPane.activate(this)
	}

	fun requestFocus() {
		rawTextArea.requestFocusInWindow()
	}

	protected open fun addFileActions(menu: JMenu) {
	}

	protected open fun addEditActions(menu: JMenu): Boolean {
		return false
	}

	protected open fun addSchemeActions(menu: JMenu): Boolean {
		return false
	}

	protected open fun addToggleActions(menu: JMenu) {
	}

	protected open fun fillLeftToolBar(toolBar: ToolBar) {
	}

	protected open fun fillBottomToolBar(toolBar: ToolBar) {
	}

	private fun updateWordWrap() {
		rawTextArea.setWordWrap(profileManager.profile().wordWrap)
		refTextArea.setWordWrap(profileManager.profile().wordWrap)
	}

	protected fun updateProfile(profileId: String?) {
		profileId?.let {
			profileManager.overrideProfile(it)
		}

		profileComboBox.selectedItem = profileManager.profile().name
		updateWordWrap()
	}

	fun closed() {
		commandControl.retrieveCommand()
	}

	private fun updateSplitPaneDividerLocation(splitPane: JSplitPane) {
		val location = environment.configurationProvider.get(MainLayout).dividerLocation
		if (location > 0) {
			splitPane.dividerLocation = location
		}
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

	private fun createProfileComboBox(): ComboBoxWithPreferredSize<Any> {
		val comboBox: ComboBoxWithPreferredSize<Any> = ComboBoxWithPreferredSize(profileManager.profiles().map { it?.name ?: ProfileSeparator() }.toTypedArray())
		val initialProfile = profileManager.profile()
		comboBox.selectedItem = initialProfile.name

		comboBox.renderer = object : DefaultListCellRenderer() {
			override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
				if (value is ProfileSeparator) {
					val separator = JLabel()
					separator.border = BorderFactory.createMatteBorder(1, 0, 0, 0, list.foreground)
					return separator
				}

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
				lastSelected = initialProfile.name
			}

			override fun itemStateChanged(e: ItemEvent?) {
				val item = comboBox.selectedItem
				if (item is ProfileSeparator) {
					Ui.runInEdt {
						for (index in comboBox.selectedIndex - 1 downTo 0) {
							(comboBox.getItemAt(index) as? OpenAIConfiguration.Profile.Name)?.let {
								profileManager.setProfile(it)
								return@runInEdt
							}
						}
						for (index in comboBox.selectedIndex + 1 until comboBox.itemCount) {
							(comboBox.getItemAt(index) as? OpenAIConfiguration.Profile.Name)?.let {
								profileManager.setProfile(it)
								return@runInEdt
							}
						}
					}
					return
				}

				(item as? OpenAIConfiguration.Profile.Name)?.let {
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

	private fun createMenuBar(): JMenuBar {
		val menuBar = JMenuBar()
		val fileMenu = JMenu("File")
		addAction(fileMenu, NewAction(tabbedPane, environment, dialogDisplayer))
		addAction(fileMenu, OpenAction(tabbedPane, environment, dialogDisplayer))
		addFileActions(fileMenu)
		fileMenu.addSeparator()
		fileMenu.add(submitAction)
		fileMenu.addSeparator()
		addAction(fileMenu, ExitAction(environment))
		menuBar.add(fileMenu)

		val editMenu = JMenu("Edit")
		addAction(editMenu, applyChangeAction)
		editMenu.addSeparator()
		if (addEditActions(editMenu)) {
			editMenu.addSeparator()
		}
		addAction(editMenu, toggleSelectionMode)
		editMenu.addSeparator()
		if (GuiConfiguration.isHotkeySupported()) {
			addAction(editMenu, ToggleHotkeyAction(environment.hotkeyListener, environment.configurationProvider, environment.accelerators, dialogDisplayer))
		}
		addAction(editMenu, TogglePasteOnCloseAction(environment.configurationProvider, environment.accelerators))
		menuBar.add(editMenu)

		val viewMenu = JMenu("View")
		addAction(viewMenu, toggleDarkModeAction)
		if (GuiConfiguration.isSystemTraySupported()) {
			addAction(viewMenu, ToggleSystemTrayAction(environment.configurationProvider, environment.accelerators))
		}
		menuBar.add(viewMenu)

		val profileMenu = JMenu("Profile")
		val profileCurrentMenu = JMenu("Current")
		val profileRadioButtonGroup = ButtonGroup()
		for (profile in profileManager.profiles()) {
			if (profile == null) {
				profileCurrentMenu.add(JSeparator())
			}
			else {
				addAction(profileCurrentMenu, SetProfileAction(profile.name, profileManager, profileRadioButtonGroup))
			}
		}
		profileMenu.add(profileCurrentMenu)
		profileMenu.addSeparator()
		addToggleActions(profileMenu)
		addAction(profileMenu, toggleShowDiffBeforeAfterAction)
		addAction(profileMenu, ToggleWordWrapAction(rawTextArea, refTextArea, profileManager, environment.accelerators))
		profileMenu.addSeparator()
		addAction(profileMenu, ToggleSubmitOnInvocationAction(profileManager, environment.accelerators))
		addAction(profileMenu, ToggleSubmitOnProfileChangeAction(profileManager, environment.accelerators))
		profileMenu.addSeparator()
		addAction(profileMenu, SelectProfileAction(profileComboBox, environment))
		menuBar.add(profileMenu)

		val schemeMenu = JMenu("Scheme")
		if (addSchemeActions(schemeMenu)) {
			menuBar.add(schemeMenu)
		}

		val helpMenu = JMenu("Help")
		addAction(helpMenu, AcknowledgmentsAction(environment, dialogDisplayer))
		helpMenu.addSeparator()
		addAction(helpMenu, AboutAction(environment, dialogDisplayer))
		menuBar.add(helpMenu)
		return menuBar
	}

	private fun createToolBar(): ToolBar {
		val toolBar = ToolBar(environment.theme, true)
		toolBar.add(submitAction)
		toolBar.add(profileComboBox)
		toolBar.add(Box.createRigidArea(Dimension(5, 0)))
		fillLeftToolBar(toolBar)
		toolBar.add(toggleSelectionMode)
		toolBar.add(Box.createHorizontalGlue())
		toolBar.add(applyChangeAction)
		toolBar.add(toggleShowDiffBeforeAfterAction)
		toolBar.add(toggleDarkModeAction)
		return toolBar
	}

	private fun configureSubmitOnInvocation() {
		var listener: ((state: State, last: State) -> Unit)? = null
		listener = { state, last ->
			if (state.diff.raw.isNotEmpty() && last.diff.raw.isEmpty()) {
				diffManager.removeStateListener(listener!!)

				if (profileManager.profile().submitOnInvocation && rawTextArea.getText().split("\n", " ", "\t").size >= 2) { // Do not submit single words, this should prevent submitting passwords.
					submitter.run()
				}
			}
		}
		diffManager.addStateListener(listener)
	}

	protected class ComboBoxWithPreferredSize<T>(entries: Array<T>) : JComboBox<T>(entries) {
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

	private class ProfileSeparator {
	}

	companion object {
		fun addAction(menu: JMenu, action: MainMenuAction) {
			val radioButtonGroup = action.getRadioButtonGroup()
			menu.add(
				if (radioButtonGroup != null) {
					val item = JRadioButtonMenuItem(action)
					radioButtonGroup.add(item)
					item
				}
				else if (action.isSelectable()) {
					JCheckBoxMenuItem(action)
				}
				else {
					JMenuItem(action)
				}
			)
		}
	}
}