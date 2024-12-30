/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.gui.toolbar.*
import dev.aibtra.main.content.*
import dev.aibtra.ai.*
import java.awt.*
import java.awt.event.*
import java.nio.file.*
import javax.swing.*

internal class ResolverTab(tabbedPane: MainTabbedPane, environment: Environment, dialogDisplayer: DialogDisplayer) : MainTab(tabbedPane, environment, dialogDisplayer) {
	private val profileManager: ResolverProfileManager
	private val resolverManager: ResolverManager
	private val resolverSaver: ResolverSaver
	private val draftEditor: ResolverDraftEditor
	private val resolutionEditor: ResolverResolutionEditor
	private val scrollListener: ScrollListener
	private val requestManager: ResolverRequestManager

	private val profileComboBox: JComboBox<Any>
	private val saveAction: MainMenuAction
	private val rebuildAction: MainMenuAction
	private val resolveOnlyAction: MainMenuAction
	private val applyResolutionAction: MainMenuAction
	private val applyChangeAction: MainMenuAction
	private val showDebugDetails: MainMenuAction

	private var titleDetails : String? = null

	init {
		resolverManager = ResolverManager(environment.coroutineDispatcher, environment.mainScope)
		profileManager = ResolverProfileManager(environment.configurationProvider)
		resolverSaver = ResolverSaver(resolverManager, environment, dialogDisplayer)
		val focusGroup = TextEditorFocusGroup()
		draftEditor = ResolverDraftEditor(focusGroup, environment)
		resolutionEditor = ResolverResolutionEditor(focusGroup, environment)
		scrollListener = ScrollListener.install(draftEditor, resolutionEditor, resolverManager.summaryScrollState) { false }

		profileComboBox = createProfileComboBox()

		requestManager = ResolverRequestManager(resolverManager, environment, dialogDisplayer) { text ->
			Ui.runInEdt {
				titleDetails = text
				updateTitle()
			}
		}

		saveAction = ResolverSaveAction(resolverSaver, environment)
		rebuildAction = ResolverRebuildAction(resolverManager, resolverSaver, requestManager, profileManager, environment.accelerators)
		resolveOnlyAction = ResolverResolveOnlyAction(resolverManager, requestManager, profileManager, environment.accelerators)
		applyResolutionAction = ResolverApplyResolutionAction(draftEditor, resolutionEditor, resolverManager, environment.accelerators)
		applyChangeAction = ResolverApplyChangeAction(draftEditor, resolutionEditor, resolverManager, environment.accelerators)
		showDebugDetails = ResolverShowDebugDetailsAction(draftEditor, resolverManager, environment.guiConfiguration, dialogDisplayer, environment.accelerators)
	}

	override fun init(mainPanel: JPanel, overlayPanel: JPanel) {
		val rawControl = createDraftControl()
		val refControl = createResolutionControl()
		val splitPane = MainSplitPane(rawControl, refControl, environment)
		val contentPanel = JPanel()
		contentPanel.layout = BorderLayout()
		contentPanel.add(splitPane.control, BorderLayout.CENTER)

		mainPanel.add(contentPanel)

		requestManager.addProgressListener { inProgress ->
			overlayPanel.isVisible = inProgress
		}

		val textRefresher = DelayedUiRefresher(100) {
			resolverManager.state.snippets?.let {
				resolverManager.updateDraftSummaryContent(draftEditor.createSummaryContentWithoutConflicts())
			}
		}

		resolverManager.addStateListener { state, lastState ->
			if (state.summaries != lastState.summaries) {
				state.summaries?.let {
					draftEditor.update(it.drafts)
					resolutionEditor.update(it.resolutions)
				}
			}
		}

		draftEditor.addContentListener {
			textRefresher.refresh()
		}

		draftEditor.addPopupMenu { pos, popupMenu ->
			ResolverShowDebugDetailsAction.createPopupAction(pos, resolverManager, environment.guiConfiguration, dialogDisplayer)?.let {
				popupMenu.add(it)
			}
		}

		resolutionEditor.addPopupMenu { pos, popupMenu ->
			ResolverApplyResolutionAction.createPopupAction(pos, resolverManager, draftEditor)?.let {
				popupMenu.add(it)
			} ?: run {
				ResolverApplyChangeAction.createPopupAction(resolverManager, resolutionEditor, draftEditor)?.let {
					popupMenu.add(it)
				}
			}
		}

		resolutionEditor.addPopupMenu(leftMouseButton = true) { _, popupMenu ->
			ResolverApplyChangeAction.createPopupAction(resolverManager, resolutionEditor, draftEditor)?.let {
				popupMenu.add(it)
			}
		}
	}

	override fun dispose() {
		toolBar.dispose()
	}

	override fun closed() {
	}

	override fun getTitle(): String {
		return titleDetails?.let {
			"<conflicts: $it...>"
		} ?: "<conflicts>"
	}

	override fun addFileActions(fileMenu: JMenu): Boolean {
		fileMenu.add(saveAction)
		fileMenu.addSeparator()
		fileMenu.add(rebuildAction)
		fileMenu.add(resolveOnlyAction)
		return true
	}

	override fun addEditActions(editMenu: JMenu): Boolean {
		addAction(editMenu, applyResolutionAction)
		addAction(editMenu, applyChangeAction)
		return true
	}

	override fun addViewActions(viewMenu: JMenu): Boolean {
		addAction(viewMenu, showDebugDetails)
		return true
	}

	override fun fillToolBarLeft(bar: ToolBar) {
		bar.add(saveAction)
		bar.add(profileComboBox)
		bar.add(rebuildAction)
		bar.add(resolveOnlyAction)
	}

	override fun fillToolBarRight(bar: ToolBar) {
		bar.add(applyResolutionAction)
		bar.add(applyChangeAction)
	}

	override fun checkClose(runnable: Runnable) {
		resolverSaver.checkSave {
			runnable.run()
		}
	}

	fun initialize(overviewFile: Path) {
		requestManager.submit(ResolverRequestManager.Request(overviewFile, true, null, profileManager.profile()))
	}

	private fun createDraftControl(): Component {
		return draftEditor.getControl()
	}

	private fun createResolutionControl(): Component {
		return resolutionEditor.getControl()
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
				if (value is AIProfile.Name) {
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
			private var lastSelected: AIProfile.Name

			init {
				lastSelected = initialProfile.name
			}

			override fun itemStateChanged(e: ItemEvent?) {
				val item = comboBox.selectedItem
				if (item is ProfileSeparator) {
					Ui.runInEdt {
						for (index in comboBox.selectedIndex - 1 downTo 0) {
							(comboBox.getItemAt(index) as? AIProfile.Name)?.let {
								profileManager.setProfile(it)
								return@runInEdt
							}
						}
						for (index in comboBox.selectedIndex + 1 until comboBox.itemCount) {
							(comboBox.getItemAt(index) as? AIProfile.Name)?.let {
								profileManager.setProfile(it)
								return@runInEdt
							}
						}
					}
					return
				}

				(item as? AIProfile.Name)?.let {
					profileManager.setProfile(it)
				}
			}
		})

		profileManager.addListener { _, name ->
			comboBox.selectedItem = name
		}

		return comboBox
	}
}
