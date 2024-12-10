/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.diff.*
import dev.aibtra.refiner.RefinerDiffManager.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.gui.toolbar.*
import dev.aibtra.main.content.*
import dev.aibtra.openai.*
import dev.aibtra.refiner.*
import java.awt.*
import java.awt.event.*
import java.nio.file.*
import javax.swing.*

internal abstract class RefinerTextualTab(initialWorkingMode: WorkingMode, private val tabbedPane: MainTabbedPane, environment: Environment, dialogDisplayer: DialogDisplayer) : MainTab(tabbedPane, environment, dialogDisplayer) {
	private val commandControl: RefinerCommandControl
	protected val rawTextArea: RefinerRawTextArea
	private val refTextArea: RefinerRefTextArea
	protected val diffManager: RefinerDiffManager
	protected val profileManager: RefinerProfileManager
	protected val requestManager: RefinerRequestManager
	private val bottomToolBar: ToolBar
	private val submitter: RefinerSubmitter
	private val submitAction: MainMenuAction
	private val applyChangeAction: MainMenuAction
	protected val profileComboBox: JComboBox<Any>
	private val toggleSelectionMode: RefinerToggleSelectionModeAction
	private val toggleShowDiffBeforeAfterAction: MainMenuAction
	private val scrollListener: ScrollListener

	init {
		val coroutineDispatcher = environment.coroutineDispatcher
		val mainScope = environment.mainScope
		diffManager = RefinerDiffManager(::normalizeText, coroutineDispatcher, mainScope, environment.debugLog)

		profileManager = RefinerProfileManager(initialWorkingMode, environment.configurationProvider)

		rawTextArea = RefinerRawTextArea({ text -> diffManager.updateRawText(text, null, profileManager.profile().diffConfig, normalization = Normalization.INITIALIZE) }, environment)
		refTextArea = RefinerRefTextArea(environment)

		val textRefresher = DelayedUiRefresher(100) {
			updateContent()
		}
		rawTextArea.addContentListener {
			textRefresher.refresh()
		}
		rawTextArea.addSelectionListener { _ ->
			textRefresher.refresh()
		}

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
						rawTextArea.scrollTo(ScrollState.ScrollPos(1, 10), ScrollState.ScrollMode.FORCE_TOP)
					}
					else {
						refTextArea.scrollTo(diffManager.scrollState.syncRightScrollPos(), ScrollState.ScrollMode.FORCE_TOP)
					}
				}
			}
		}

		profileManager.addListener { _, name ->
			updateWordWrap()
		}

		requestManager = RefinerRequestManager(diffManager, coroutineDispatcher, mainScope, dialogDisplayer)

		scrollListener = ScrollListener(diffManager.scrollState) { diffManager.state.selection }
		scrollListener.install(rawTextArea, refTextArea)

		profileComboBox = createProfileComboBox()

		commandControl = createCommandControl()

		bottomToolBar = ToolBar(environment.theme, false)

		submitter = createSubmitter()

		submitAction = RefinerSubmitAction(environment, diffManager, requestManager, submitter)
		applyChangeAction = RefinerApplyChangeAction(refTextArea, rawTextArea, diffManager, environment.accelerators)
		toggleSelectionMode = RefinerToggleSelectionModeAction(diffManager, profileManager, rawTextArea, environment.accelerators)
		toggleShowDiffBeforeAfterAction = RefinerToggleShowRefBeforeAndAfterAction(diffManager, profileManager, environment.accelerators)
	}

	override fun init(mainPanel: JPanel, overlayPanel: JPanel) {
		val rawControl = createRawControl()
		val refControl = createRefControl()
		val splitPane = MainSplitPane(rawControl, refControl, environment)

		val contentPanel = JPanel()
		contentPanel.layout = BorderLayout()
		contentPanel.add(commandControl.getComponent(), BorderLayout.NORTH)
		contentPanel.add(splitPane.control, BorderLayout.CENTER)
		mainPanel.add(contentPanel)

		requestManager.addProgressListener { inProgress ->
			overlayPanel.isVisible = inProgress
		}

		fillBottomToolBar(bottomToolBar)
		if (bottomToolBar.isNotEmpty()) {
			contentPanel.add(bottomToolBar.getComponent(), BorderLayout.SOUTH)
		}

		profileManager.fireInitialization() // to adjust all actions

		environment.paths.getProperty("simulateOutputTextFile")?.let {
			diffManager.updateRefText(Files.readString(Path.of(it)), true)
		}

		configureSubmitOnInvocation()
	}

	override fun dispose() {
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
		refTextArea.addPopupMenu { _, popupMenu ->
			refTextArea.getSelectionRange()?.let {
				val blocks = RefinerDiffManager.getSelectedBlocksFromRef(diffManager.state, it)
				popupMenu.apply {
					if (blocks.isNotEmpty()) {
						add(JMenuItem(applyChangeAction))
					}
					if (componentCount > 0) {
						add(JSeparator())
					}
					add(JMenuItem(RefinerCopyRefSelectionAction(refTextArea)))
				}
			}
		}

		refTextArea.addPopupMenu(leftMouseButton = true) { _, popupMenu ->
			refTextArea.getSelectionRange()?.let {
				val blocks = RefinerDiffManager.getSelectedBlocksFromRef(diffManager.state, it)
				popupMenu.apply {
					if (blocks.isNotEmpty()) {
						add(JMenuItem(applyChangeAction))
					}
					if (componentCount > 0) {
						add(JSeparator())
					}
					add(JMenuItem(RefinerCopyRefSelectionAction(refTextArea)))
				}
			}
		}

		return control
	}

	fun requestFocus() {
		rawTextArea.requestFocusInWindow()
	}

	override fun addFileActions(fileMenu: JMenu) : Boolean {
		addAction(fileMenu, RefinerNewAction(tabbedPane, environment, this.dialogDisplayer))
		addAction(fileMenu, RefinerOpenAction(tabbedPane, environment, this.dialogDisplayer))
		addTextualFileActions(fileMenu)
		fileMenu.addSeparator()
		fileMenu.add(submitAction)
		return true
	}

	protected open fun addTextualFileActions(menu: JMenu) {
	}

	override fun addEditActions(editMenu: JMenu): Boolean {
		addAction(editMenu, applyChangeAction)
		editMenu.addSeparator()
		if (addTextualEditActions(editMenu)) {
			editMenu.addSeparator()
		}
		addAction(editMenu, toggleSelectionMode)
		editMenu.addSeparator()
		if (GuiConfiguration.isPasteOnCloseSupported()) {
			addAction(editMenu, RefinerTogglePasteOnCloseAction(environment.configurationProvider, environment.accelerators))
		}
		return false
	}

	protected open fun addTextualEditActions(menu: JMenu): Boolean {
		return false
	}

	override fun addProfileActions(profileMenu: JMenu): Boolean {
		val profileCurrentMenu = JMenu("Current")
		val profileRadioButtonGroup = ButtonGroup()
		for (profile in profileManager.profiles()) {
			if (profile == null) {
				profileCurrentMenu.add(JSeparator())
			}
			else {
				addAction(profileCurrentMenu, RefinerSetProfileAction(profile.name, profileManager, profileRadioButtonGroup))
			}
		}
		profileMenu.add(profileCurrentMenu)
		profileMenu.addSeparator()
		addToggleActions(profileMenu)
		addAction(profileMenu, toggleShowDiffBeforeAfterAction)
		addAction(profileMenu, RefinerToggleWordWrapAction(rawTextArea, refTextArea, profileManager, environment.accelerators))
		profileMenu.addSeparator()
		addAction(profileMenu, RefinerToggleSubmitOnInvocationAction(profileManager, environment.accelerators))
		addAction(profileMenu, RefinerToggleSubmitOnProfileChangeAction(profileManager, environment.accelerators))
		profileMenu.addSeparator()
		addAction(profileMenu, RefinerSelectProfileAction(profileComboBox, environment))
		return true
	}

	protected open fun addToggleActions(menu: JMenu) {
	}

	override fun fillToolBarLeft(bar: ToolBar) {
		bar.add(submitAction)
		bar.add(profileComboBox)
		bar.add(Box.createRigidArea(Dimension(5, 0)))
		fillTopToolBarLeft(bar)
		bar.add(toggleSelectionMode)
	}

	override fun fillToolBarRight(bar: ToolBar) {
		bar.add(applyChangeAction)
		bar.add(toggleShowDiffBeforeAfterAction)
	}

	protected open fun fillTopToolBarLeft(toolBar: ToolBar) {
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

	override fun closed() {
		commandControl.retrieveCommand()
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
				if (value is OpenAIProfile.Name) {
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
			private var lastSelected: OpenAIProfile.Name

			init {
				lastSelected = initialProfile.name
			}

			override fun itemStateChanged(e: ItemEvent?) {
				val item = comboBox.selectedItem
				if (item is ProfileSeparator) {
					Ui.runInEdt {
						for (index in comboBox.selectedIndex - 1 downTo 0) {
							(comboBox.getItemAt(index) as? OpenAIProfile.Name)?.let {
								profileManager.setProfile(it)
								return@runInEdt
							}
						}
						for (index in comboBox.selectedIndex + 1 until comboBox.itemCount) {
							(comboBox.getItemAt(index) as? OpenAIProfile.Name)?.let {
								profileManager.setProfile(it)
								return@runInEdt
							}
						}
					}
					return
				}

				(item as? OpenAIProfile.Name)?.let {
					profileManager.setProfile(it)
				}
			}
		})

		profileManager.addListener { _, name ->
			comboBox.selectedItem = name
		}

		return comboBox
	}

	private fun createCommandControl(): RefinerCommandControl {
		fun updateProfile(commandControl: RefinerCommandControl) {
			commandControl.setProfile(profileManager.profile())
		}

		val commandControl = RefinerCommandControl(environment.configurationProvider)
		profileManager.addListener { _, _ ->
			updateProfile(commandControl)
		}
		updateProfile(commandControl)
		return commandControl
	}

	private fun createSubmitter(): RefinerSubmitter {
		val submitter = RefinerSubmitter(environment, requestManager, commandControl, dialogDisplayer) { profileManager.profile() }
		profileManager.addListener { lastName, name ->
			val profile = profileManager.profile()
			diffManager.setConfig(profile.diffConfig)

			if (profileManager.profile().submitOnProfileChange && lastName != name && diffManager.state.rawText.all.isNotBlank()) {
				submitter.run()
			}
		}
		return submitter
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

	private class ProfileSeparator
}