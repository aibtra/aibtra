/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.WorkingMode
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import dev.aibtra.gui.toolbar.ToolBar
import dev.aibtra.openai.OpenAIProfiles
import dev.aibtra.text.Schemes
import dev.aibtra.text.TextNormalizer
import java.awt.Component
import javax.swing.*

internal class MainTextTab(private val workingMode: WorkingMode, tabbedPane: MainTabbedPane, private val environment: Environment, dialogDisplayer: DialogDisplayer) : MainTab(workingMode, tabbedPane, environment, dialogDisplayer) {
	private val copyAndCloseAction: MainMenuAction
	private val schemeComboBox: JComboBox<Schemes.Scheme>
	private val toggleFilterMarkdownAction: MainMenuAction

	private var skipCloseCheck = false

	init {
		schemeComboBox = createSchemeComboBox()

		copyAndCloseAction = CopyAndCloseAction(this, environment, requestManager, diffManager, rawTextArea, environment.configurationProvider)
		toggleFilterMarkdownAction = ToggleFilterMarkdownAction(diffManager, profileManager, environment.accelerators)

		init()
	}

	override fun getTitle(): String {
		return if (workingMode == WorkingMode.CLIPBOARD) "<clipboard>" else "<text>"
	}

	override fun normalizeText(raw: String): String {
		val scheme = schemeComboBox.selectedItem as? Schemes.Scheme
		return scheme?.let {
			TextNormalizer(it.textNormalizerConfig).normalize(raw)
		} ?: raw
	}

	override fun checkClose(runnable: Runnable) {
		if (!skipCloseCheck && rawTextArea.getText().isNotEmpty()) {
			toFront()

			Dialogs.showConfirmationDialog("Close", "Do you want to discard this text?", "Discard", dialogDisplayer) {
				runnable.run()
			}
		}
		else {
			runnable.run()
		}
	}

	override fun addEditActions(menu: JMenu) : Boolean {
		addAction(menu, copyAndCloseAction)
		return true
	}

	override fun addToggleActions(menu: JMenu) {
		addAction(menu, toggleFilterMarkdownAction)
	}

	override fun addSchemeActions(menu: JMenu) : Boolean {
		val onPasteMenu = JMenu("On Paste")
		addAction(onPasteMenu, TextNormalizerAction.createJoinLines(environment.configurationProvider, environment.accelerators))
		onPasteMenu.addSeparator()
		addAction(onPasteMenu, TextNormalizerAction.createChangeDoubleToSingleBlockQuotes(environment.configurationProvider, environment.accelerators))
		addAction(onPasteMenu, TextNormalizerAction.createFixMissingEmptyLineAfterBlockQuote(environment.configurationProvider, environment.accelerators))
		addAction(onPasteMenu, TextNormalizerAction.createRewrapBlockQuotes(environment.configurationProvider, environment.accelerators))

		menu.add(onPasteMenu)
		return true
	}

	override fun fillLeftToolBar(toolBar: ToolBar) {
		toolBar.add(schemeComboBox)
	}

	override fun fillBottomToolBar(toolBar: ToolBar) {
		toolBar.add(copyAndCloseAction)
	}

	fun setProfile(profileId: String?) {
		updateProfile(profileId)
	}

	fun setText(text: String, workingMode: WorkingMode, profileId: String?) {
		require(workingMode == WorkingMode.CLIPBOARD)

		updateProfile(profileId)

		// Better use the default scheme only when explicitly pasting from clipboard;
		// a simple "New" should not set the last used scheme.
		val configurationProvider = environment.configurationProvider
		val schemes = configurationProvider.get(Schemes)
		schemes.list.find { it.name == schemes.currentName }?.let {
			schemeComboBox.selectedItem = it
		}

		rawTextArea.initializeText(text)
	}

	fun initiateClose(runnable: Runnable) {
		skipCloseCheck = true
		try {
			environment.frameManager.close(runnable)

			toFront()
		} finally {
			skipCloseCheck = false
		}
	}

	private fun createSchemeComboBox(): JComboBox<Schemes.Scheme> {
		val configurationProvider = environment.configurationProvider
		val schemes = configurationProvider.get(Schemes)
		val comboBox = ComboBoxWithPreferredSize(schemes.list.toTypedArray())

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

		fun updateEnabledState() {
			(profileComboBox.selectedItem as? OpenAIProfiles.Profile.Name)?.let { name ->
				profileManager.getProfile(name)?.let { profile ->
					comboBox.isEnabled = profile.supportsSchemes
				}
			}
		}

		profileManager.addListener { _, _ ->
			updateEnabledState()
		}
		updateEnabledState()
		return comboBox
	}
}
