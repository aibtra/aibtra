/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.action.*
import dev.aibtra.main.content.*

class ResolverApplyChangeAction(
	private val draftEditor: ResolverDraftEditor,
	private val resolutionEditor: ResolverResolutionEditor,
	private val resolverManager: ResolverManager,
	accelerators: Accelerators
) :
	MainMenuAction("applyChange", "Apply", Icons.ACCEPT, "Apply", "alt LEFT", accelerators, ActionRunnable {
		resolutionEditor.getSelectionRange()?.let { selectionRange ->
			createApply(selectionRange, resolverManager)?.let {
				it(draftEditor)
			}
		}
	}) {
	init {
		resolutionEditor.addFocusListener {
			updateEnabledState()
		}
		resolutionEditor.addCaretListener {
			updateEnabledState()
		}

		isEnabled = false
	}

	private fun updateEnabledState() {
		isEnabled = resolutionEditor.hasFocus() && resolutionEditor.getSelectionRange()?.let {
			selectionRange -> createApply(selectionRange, resolverManager)
		} != null
	}

	companion object {
		fun createPopupAction(resolverManager: ResolverManager, resolutionTextArea: ResolverResolutionEditor, draftTextArea: ResolverDraftEditor): DefaultAction? {
			return resolutionTextArea.getSelectionRange()?.let { range ->
				createApply(range, resolverManager)?.let { apply ->
					object : DefaultAction("Apply", ActionRunnable {
						apply(draftTextArea)
					}) {
					}
				}
			}
		}

		private fun createApply(range: IntRange, resolverManager: ResolverManager): ((ResolverDraftEditor) -> Unit)? {
			val state = resolverManager.state
			val summaries = requireNotNull(state.summaries)
			val resolutionsContent = summaries.resolutions.content
			val fromAbs = range.first
			val toAbs = range.last
			resolutionsContent.findSnippetAt(fromAbs)?.let { resolutionSnippet ->
				val resolutionSnippetTo = resolutionsContent.findSnippetAt(toAbs)
				if (resolutionSnippet == resolutionSnippetTo) {
					val resolutionOffset = requireNotNull(resolutionsContent.getSnippetRange(resolutionSnippet)).first
					val from = fromAbs - resolutionOffset
					val to = toAbs - resolutionOffset
					state.diffs?.get(resolutionSnippet.id)?.let { diff ->
						val filtered = diff.blocks.filter {
							it.refFrom >= from && it.refTo <= to + 1 // the +1 is required if there is a deletion in the resolution text in the last position
						}.toList()

						if (filtered.isNotEmpty()) {
							return { draftTextArea ->
								val draftContent = summaries.drafts.content
								require(draftTextArea.createSummaryContentWithoutConflicts().text == draftContent.text)

								draftContent.getHeader(resolutionSnippet)?.let { draftHeader -> // sanity check
									val draftStart = draftHeader.contentRange.first
									for (block in filtered.reversed()) {
										val draftRange = IntRange(draftStart + block.rawFrom, draftStart + block.rawTo)
										draftTextArea.replaceText(draftRange, resolutionsContent.text.substring(resolutionOffset + block.refFrom, resolutionOffset + block.refTo))
									}
								}
							}
						}
					}
				}
			}

			return null
		}
	}
}

