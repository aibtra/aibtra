/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.action.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import net.miginfocom.swing.*
import java.awt.*
import javax.swing.*

class ResolverShowDebugDetailsAction(
	private val draftTextArea: ResolverDraftTextArea,
	private val resolverManager: ResolverManager,
	private val guiConfiguration: GuiConfiguration,
	private val dialogDisplayer: DialogDisplayer,
	accelerators: Accelerators
) :
	MainMenuAction("showDebugDetails", TITLE, null, accelerators, ActionRunnable {
		createShow(draftTextArea.getCaretPosition(), resolverManager, guiConfiguration, dialogDisplayer)?.run()
	}) {
	init {
		draftTextArea.addCaretListener {
			updateEnabledState()
		}

		isEnabled = false
	}

	private fun updateEnabledState() {
		isEnabled = createShow(draftTextArea.getCaretPosition(), resolverManager, guiConfiguration, dialogDisplayer) != null
	}

	companion object {
		const val TITLE = "Show Debug Details"

		private fun createShow(position: Int, resolverManager: ResolverManager, guiConfiguration: GuiConfiguration, dialogDisplayer: DialogDisplayer): Runnable? {
			val state = resolverManager.state
			return state.summaries?.let { summaries ->
				state.resolutions?.let { resolutions ->
					summaries.drafts.content.findSnippetAt(position)?.let { snippet ->
						val idToStepToDebugDetails = resolutions.resolverPacket.idToStepToDebugDetails
						idToStepToDebugDetails[snippet.id]?.let { stepToDebugDetails ->
							Runnable {
								CloseDialog(TITLE) {
									val panel = JPanel()
									val layout = MigLayout(
										"fill",
										"[grow][][grow]",
										"[][][grow]"
									)
									panel.layout = layout

									val leftSelector = createComboBox()
									val rightSelector = createComboBox()
									val leftText = createTextArea(leftSelector, stepToDebugDetails, guiConfiguration)
									val rightText = createTextArea(rightSelector, stepToDebugDetails, guiConfiguration)

									panel.add(leftSelector, "cell 0 0, grow")
									panel.add(rightSelector, "cell 2 0, grow")
									panel.add(JPanel(), "cell 0 1, span 3, gapy 0")
									panel.add(JScrollPane(leftText), "cell 0 2, grow")
									panel.add(JScrollPane(rightText), "cell 2 2, grow")

									val sortedSteps = stepToDebugDetails.keys.sorted()
									for (step in sortedSteps) {
										leftSelector.addItem(step)
										rightSelector.addItem(step)
									}
									if (sortedSteps.isNotEmpty()) {
										rightSelector.selectedItem = sortedSteps.last()
									}

									CloseDialog.Content(panel)
								}.show(dialogDisplayer)
							}
						}
					} ?: Runnable {
						Dialogs.showWarning(TITLE, "No debug details found for this conflict.", dialogDisplayer)
					}
				}
			}
		}

		private fun createTextArea(selector: JComboBox<String>, stepToDebugDetails: Map<String, String>, guiConfiguration: GuiConfiguration): JScrollPane {
			val textArea = JTextArea()
			textArea.font = guiConfiguration.fonts.monospacedFont

			selector.addActionListener {
				stepToDebugDetails[selector.selectedItem]?.let {
					textArea.text = it
					textArea.caretPosition = 0
				}
			}

			val scrollPane = JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS)
			scrollPane.preferredSize = Dimension(800, 600)
			return scrollPane
		}

		private fun createComboBox() = JComboBox<String>()

		fun createPopupAction(position: Int, resolverManager: ResolverManager, guiConfiguration: GuiConfiguration, dialogDisplayer: DialogDisplayer): DefaultAction? {
			return createShow(position, resolverManager, guiConfiguration, dialogDisplayer)?.let { runnable ->
				object : DefaultAction(TITLE, ActionRunnable {
					runnable.run()
				}) {
				}
			}
		}
	}
}

