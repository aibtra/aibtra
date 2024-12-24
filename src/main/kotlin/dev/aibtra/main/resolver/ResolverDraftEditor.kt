/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.diff.*
import dev.aibtra.main.content.*
import java.awt.event.*
import javax.swing.*
import javax.swing.text.*
import javax.swing.undo.*

class ResolverDraftEditor(focusGroup: TextEditorFocusGroup, environment: Environment) : ResolverEditor(focusGroup, environment) {
	private val undoManager: UndoManager
	private val styleModified: HighlightStyle
	private val styleAdded: HighlightStyle
	private val styleRemoved: HighlightStyle
	private val styleGapLeft: HighlightStyle
	private val styleGapRight: HighlightStyle

	private var ignoreUndoableEvents = false
	private var documentFilterEnabled = true

	init {
		(textArea.document as AbstractDocument).let {
			it.documentFilter = object : DocumentFilter() {
				override fun remove(fb: FilterBypass?, offset: Int, length: Int) {
					if (documentFilterEnabled && texter.intersectsFileHeader(offset, length)) {
						return
					}

					super.remove(fb, offset, length)
				}

				override fun insertString(fb: FilterBypass?, offset: Int, string: String?, attr: AttributeSet?) {
					if (documentFilterEnabled && texter.intersectsFileHeader(offset, 1)) {
						return
					}

					super.insertString(fb, offset, string, attr)
				}

				override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
					if (documentFilterEnabled && texter.intersectsFileHeader(offset, length)) {
						return
					}

					super.replace(fb, offset, length, text, attrs)
				}
			}
		}

		styleModified = HighlightStyle({ it.rawBackgroundModified }, { null }, false, false, GapStyle.NONE)
		styleAdded = HighlightStyle({ it.rawBackgroundAdded }, { null }, false, false, GapStyle.NONE)
		styleRemoved = HighlightStyle({ it.rawBackgroundRemoved }, { null }, false, true, GapStyle.NONE)
		styleGapLeft = HighlightStyle({ it.rawBackgroundAdded }, { it.rawBackgroundAddedShadow }, false, false, GapStyle.LEFT)
		styleGapRight = HighlightStyle({ it.rawBackgroundAdded }, { it.rawBackgroundAddedShadow }, false, false, GapStyle.RIGHT)

		undoManager = UndoManager()

		textArea.document.addUndoableEditListener { e ->
			if (!ignoreUndoableEvents) {
				undoManager.addEdit(e.edit)
			}
		}

		textArea.actionMap.put("Undo", object : AbstractAction("Undo") {
			override fun actionPerformed(evt: ActionEvent?) {
				if (undoManager.canUndo()) {
					undoManager.undo()
				}
			}
		})

		textArea.actionMap.put("Redo", object : AbstractAction("Redo") {
			override fun actionPerformed(evt: ActionEvent?) {
				if (undoManager.canRedo()) {
					undoManager.redo()
				}
			}
		})

		val inputMap = textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK), "Redo")
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "Undo")
	}

	fun update(summary: ResolveSummary) {
		val initialized = texter.initialize(summary, false) { text, textArea ->
			documentFilterEnabled = false
			try {
				val lastCaretPosition = textArea.caretPosition
				textArea.text = text
				textArea.caretPosition = if (lastCaretPosition < text.length) lastCaretPosition else 0
			} finally {
				documentFilterEnabled = true
			}
		}

		if (initialized) {
			undoManager.discardAllEdits()
		}

		setSummary(summary)
	}

	fun createSummaryContentWithoutConflicts(): ResolverSummaryContent {
		return texter.createSummaryContentWithoutConflicts()
	}

	fun replaceText(range: IntRange, text: String) {
		textArea.document.remove(range.first, range.last - range.first)
		textArea.document.insertString(range.first, text, null)
		textArea.caretPosition = range.first
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