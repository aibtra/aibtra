/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.diff.*
import dev.aibtra.main.content.*

class ResolverResolutionEditor(focusGroup: TextEditorFocusGroup, environment: Environment) : ResolverEditor(focusGroup, environment) {
	private val styleModified: HighlightStyle
	private val styleAdded: HighlightStyle
	private val styleRemoved: HighlightStyle
	private val styleGapLeft: HighlightStyle
	private val styleGapRight: HighlightStyle
	private val documentFilter: NonEditableDocumentFilter

	init {
		documentFilter = disableEditing()

		styleModified = HighlightStyle({ it.refBackgroundModified }, { null }, false, false, GapStyle.NONE)
		styleAdded = HighlightStyle({ it.refBackgroundAdded }, { null }, false, false, GapStyle.NONE)
		styleRemoved = HighlightStyle({ it.refBackgroundRemoved }, { null }, false, true, GapStyle.NONE)
		styleGapLeft = HighlightStyle({ it.refBackgroundRemoved }, { it.refBackgroundRemovedShadow }, false, false, GapStyle.LEFT)
		styleGapRight = HighlightStyle({ it.refBackgroundRemoved }, { it.refBackgroundRemovedShadow }, false, false, GapStyle.RIGHT)
	}

	fun update(summary: ResolveSummary) {
		texter.initialize(summary, true) { text, textArea ->
			documentFilter.update {
				textArea.text = text
				textArea.caretPosition = 0
			}
		}

		setSummary(summary)
	}

	override fun getHighlighting(char: DiffChar): HighlightStyle? {
		return when (char.kind) {
			DiffKind.EQUAL -> null
			DiffKind.ADDED -> styleAdded
			DiffKind.MODIFIED -> styleModified
			DiffKind.REMOVED -> styleRemoved
			DiffKind.GAP_LEFT -> styleGapLeft
			DiffKind.GAP_RIGHT -> styleGapRight
		}
	}
}