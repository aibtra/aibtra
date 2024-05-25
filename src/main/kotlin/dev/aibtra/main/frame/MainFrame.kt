/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.diff.DiffManager
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

class MainFrame(private val environment: Environment) {
	val dialogDisplayer: DialogDisplayer
	private val frame: JFrame
	private val rawTextArea: RawTextArea
	private val refinedTextArea: RefinedTextArea
	private val diffManager: DiffManager
	private val requestManager: RequestManager
	private val submitter: Submitter
	private val submitAction: MainMenuAction
	private val applyChangeAction: MainMenuAction
	private val copyAndCloseAction: MainMenuAction
	private val pasteAndSubmitAction: MainMenuAction
	private val schemeComboBox: JComboBox<Schemes.Scheme>
	private val profileComboBox: JComboBox<OpenAIConfiguration.Profile>
	private val toggleFilterMarkdownAction: MainMenuAction
	private val toggleShowDiffBeforeAfterAction: MainMenuAction
	private val toggleDarkModeAction: ToggleDarkModeAction

	init {
		frame = JFrame("Aibtra 1.0 alpha")
		frame.iconImage = Icons.LOGO.getImageIcon(true).image

		dialogDisplayer = create(frame)

		val coroutineDispatcher = environment.coroutineDispatcher
		diffManager = DiffManager(
			environment.configurationProvider.get(DiffManager.Config), {
				TextNormalizer(environment.configurationProvider.get(Schemes).current().textNormalizerConfig).normalize(it)
			}, coroutineDispatcher, environment.debugLog
		)

		rawTextArea = RawTextArea({ text -> diffManager.updateRaw(text, initial = true) }, environment)
		refinedTextArea = RefinedTextArea(environment)

		val diffManagerRefresher = DelayedUiRefresher(100) {
			diffManager.updateRaw(rawTextArea.getText())
		}
		rawTextArea.addListener {
			diffManagerRefresher.refresh()
		}

		diffManager.addListener { state ->
			Ui.assertEdt()

			val rawText = rawTextArea.getText()
			if (rawText == state.raw) {
				rawTextArea.setDiffCharsAndFilteredText(state.rawChars, state.filtered)
			}

			refinedTextArea.setText(state.refFormatted, state.refChars)
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

		schemeComboBox = createSchemeComboBox()
		profileComboBox = createProfileComboBox()
		submitter = Submitter(environment, requestManager, dialogDisplayer) { profileComboBox.selectedItem as OpenAIConfiguration.Profile }

		val submitAction = SubmitAction(environment, requestManager, submitter)
		this.submitAction = submitAction
		applyChangeAction = ApplyChangeAction(refinedTextArea, rawTextArea, diffManager, environment.accelerators)
		copyAndCloseAction = CopyAndCloseAction(environment, requestManager, diffManager, rawTextArea, environment.configurationProvider, frame)
		pasteAndSubmitAction = PasteAndSubmitAction(environment, requestManager, diffManager, rawTextArea, submitAction)
		toggleFilterMarkdownAction = ToggleFilterMarkdownAction(diffManager, environment.configurationProvider, environment.accelerators)
		toggleShowDiffBeforeAfterAction = ToggleShowRefBeforeAndAfterAction(diffManager, environment.configurationProvider, environment.accelerators)
		toggleDarkModeAction = ToggleDarkModeAction(environment.theme, environment.configurationProvider, environment.accelerators)
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
		mainToolBar.add(schemeComboBox)
		mainToolBar.add(Box.createRigidArea(Dimension(5, 0)))
		mainToolBar.add(profileComboBox)
		mainToolBar.add(applyChangeAction)
		mainToolBar.add(Box.createHorizontalGlue())
		mainToolBar.add(pasteAndSubmitAction)
		mainToolBar.add(Box.createHorizontalGlue())
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
		fillContent(centerPane)

		frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
		frame.isVisible = true

		environment.frameManager.register(this, frame, !environment.systemTrayEnabled)

		frame.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent?) {
				mainToolBar.dispose()
				bottomToolBar.dispose()
			}
		})
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
					rawTextArea.setText(it)
				}
			}
		}
		return comboBox
	}

	private fun createProfileComboBox(): JComboBox<OpenAIConfiguration.Profile> {
		val configurationProvider = environment.configurationProvider
		val initialConfiguration = configurationProvider.get(OpenAIConfiguration)
		val comboBox = ComboBoxWithPreferredSize(initialConfiguration.profiles.toTypedArray())
		initialConfiguration.profiles.find { it.name == initialConfiguration.defaultProfileName }?.let {
			comboBox.selectedItem = it
		}

		comboBox.renderer = object : DefaultListCellRenderer() {
			override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
				val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
				if (value is OpenAIConfiguration.Profile) {
					label.text = value.name
				}
				else {
					label.text = "<default instructions>"
				}

				return label
			}
		}

		comboBox.adjustWidth()

		comboBox.addItemListener {
			(comboBox.selectedItem as? OpenAIConfiguration.Profile)?.let { profile ->
				configurationProvider.change(OpenAIConfiguration) {
					it.copy(defaultProfileName = profile.name)
				}
			}
		}
		return comboBox
	}

	private fun fillContent(container: Container) {
		val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
		splitPane.resizeWeight = 0.5

		val refinedControl = refinedTextArea.createControl()
		splitPane.topComponent = rawTextArea.createControl()
		splitPane.bottomComponent = refinedControl

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

		container.add(splitPane, BorderLayout.CENTER)

		refinedTextArea.addMouseListener(object : MouseAdapter() {
			override fun mouseReleased(e: MouseEvent) {
				refinedTextArea.getSelectionRange()?.let {
					val blocks = DiffManager.getSelectedBlocksFromRefined(diffManager.state, it)
					if (blocks.isNotEmpty()) {
						val popupMenu = JPopupMenu()
						popupMenu.add(JMenuItem(applyChangeAction))
						popupMenu.show(e.component, e.x, e.y)
					}
				}
			}
		})
	}

	private fun fillMenuBar(menuBar: JMenuBar) {
		val fileMenu = JMenu("File")
		addAction(fileMenu, ExitAction(environment))
		menuBar.add(fileMenu)

		val editMenu = JMenu("Edit")
		addAction(editMenu, applyChangeAction)
		editMenu.addSeparator()
		addAction(editMenu, copyAndCloseAction)
		addAction(editMenu, pasteAndSubmitAction)
		editMenu.addSeparator()
		addAction(editMenu, toggleFilterMarkdownAction)
		editMenu.addSeparator()
		addAction(editMenu, ToggleSubmitOnInvocationAction(environment.configurationProvider, environment.accelerators))
		editMenu.addSeparator()
		addAction(editMenu, ToggleHotkeyAction(environment.hotkeyListener, environment.configurationProvider, environment.accelerators, dialogDisplayer))
		addAction(editMenu, TogglePasteOnCloseAction(environment.configurationProvider, environment.accelerators))
		menuBar.add(editMenu)

		val viewMenu = JMenu("View")
		addAction(viewMenu, toggleShowDiffBeforeAfterAction)
		viewMenu.addSeparator()
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

		val schemeMenu = JMenu("Scheme")
		schemeMenu.add(onPasteMenu)
		menuBar.add(schemeMenu)

		val helpMenu = JMenu("Help")
		addAction(helpMenu, AcknowledgmentsAction(environment, dialogDisplayer))
		helpMenu.addSeparator()
		addAction(helpMenu, AboutAction(environment, dialogDisplayer))
		menuBar.add(helpMenu)
	}

	fun setText(text: String) {
		rawTextArea.setText(text)

		environment.paths.getProperty("simulateOutputTextFile")?.let {
			diffManager.updateRefined(Files.readString(Path.of(it)), true)
		}

		if (environment.configurationProvider.get(GuiConfiguration).submitOnInvocation &&
			rawTextArea.getText().split("\n", " ", "\t").size >= 2) { // Do not submit single words, this should prevent submitting passwords.

			var listener: ((DiffManager.State) -> Unit)? = null
			listener = {
				diffManager.removeListener(listener!!)
				submitter.run()
			}
			diffManager.addListener(listener)
		}
	}

	fun toFront() {
		frame.toFront()
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
}