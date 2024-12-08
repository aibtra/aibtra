/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.action.*
import dev.aibtra.main.content.*

class ResolverApplyResolutionAction(
	draftTextArea: ResolverDraftTextArea,
	private val resolutionTextArea: ResolverResolutionTextArea,
	private val resolverManager: ResolverManager,
	accelerators: Accelerators
) :
	MainMenuAction("applyResolution", "Apply Resolution", Icons.APPLY_RESOLUTION, "Apply Resolution", "alt ENTER", accelerators, ActionRunnable {
		createApply(resolutionTextArea.getCaretPosition(), resolverManager)?.let {
			it(draftTextArea)
		}
	}) {
	init {
		resolutionTextArea.addFocusListener {
			updateEnabledState()
		}
		resolutionTextArea.addCaretListener {
			updateEnabledState()
		}

		isEnabled = false
	}

	private fun updateEnabledState() {
		isEnabled = resolutionTextArea.hasFocus() && createApply(resolutionTextArea.getCaretPosition(), resolverManager) != null
	}

	companion object {
		private fun createApply(position: Int, resolverManager: ResolverManager): ((ResolverDraftTextArea) -> Unit)? {
			val state = resolverManager.state
			val summaries = requireNotNull(state.summaries)
			return state.resolutions?.let { resolutions ->
				val resolutionsContent = requireNotNull(summaries.resolutions).content
				val resolution = resolutionsContent.findConflictAt(position)?.let {
					resolutions[it]
				}

				resolution?.let { res ->
					state.texts?.let {
						val text = it[res.snippet]
						text.conflictRange?.let {
							{ draftTextArea ->
								val draftContent = summaries.drafts.content
								require(draftTextArea.createSummaryContentWithoutConflicts().text == draftContent.text)

								draftContent.getHeader(res.snippet)?.let { draftHeader -> // sanity check
									draftHeader.conflictRange?.let { draftRange ->
										draftTextArea.replaceText(draftRange, res.content.text.removeSuffix("\n"))
									}
								}
							}
						}
					}
				}
			}
		}

		fun createPopupAction(position: Int, resolverManager: ResolverManager, draftTextArea: ResolverDraftTextArea): DefaultAction? {
			return createApply(position, resolverManager)?.let { apply ->
				object : DefaultAction("Apply Resolution", ActionRunnable {
					apply(draftTextArea)
				}) {
				}
			}
		}
	}
}

