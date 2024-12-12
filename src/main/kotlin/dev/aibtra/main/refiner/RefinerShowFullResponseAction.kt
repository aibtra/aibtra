/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.action.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.refiner.*
import net.miginfocom.swing.*
import java.awt.*
import javax.swing.*

class RefinerShowFullResponseAction(
	private val diffManager: RefinerDiffManager,
	private val guiConfiguration: GuiConfiguration,
	private val dialogDisplayer: DialogDisplayer,
	accelerators: Accelerators
) :
	MainMenuAction("showDebugDetails", TITLE, null, accelerators, ActionRunnable {
		createShow(diffManager, guiConfiguration, dialogDisplayer)?.run()
	}) {
	init {
		isEnabled = false

		diffManager.addStateListener { _, _ ->
			updateEnabledState()
		}
	}

	private fun updateEnabledState() {
		isEnabled = createShow(diffManager, guiConfiguration, dialogDisplayer) != null
	}

	companion object {
		const val TITLE = "Show Full Response"

		private fun createShow(diffManager: RefinerDiffManager, guiConfiguration: GuiConfiguration, dialogDisplayer: DialogDisplayer): Runnable? {
			val state = diffManager.state
			return state.conversation?.entries?.lastOrNull()?.rawResponse?.let { rawResponse ->
				Runnable {
					CloseDialog(TITLE) {
						val panel = JPanel()
						val layout = MigLayout(
							"fill",
							"[grow]",
							"[grow]"
						)
						panel.layout = layout

						val textArea = JTextArea()
						textArea.font = guiConfiguration.fonts.monospacedFont
						textArea.text = rawResponse
						textArea.caretPosition = 0

						val scrollPane = JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS)
						scrollPane.preferredSize = Dimension(1200, 800)

						panel.add(scrollPane, "cell 0 0, grow")

						CloseDialog.Content(panel)
					}.show(dialogDisplayer)
				}
			}
		}
	}
}

