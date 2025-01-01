/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.diff.*
import dev.aibtra.main.content.*
import dev.aibtra.text.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.text.*
import javax.swing.undo.*

class RefinerRawEditor(private val textInitializer: TextInitializer, environment: Environment) :
	AbstractTextEditor(true, environment) {
	private val undoManager: UndoManager
	private val styleModified: HighlightStyle
	private val styleAdded: HighlightStyle
	private val styleRemoved: HighlightStyle
	private val styleGapLeft: HighlightStyle
	private val styleGapRight: HighlightStyle
	private val styleFiltered: HighlightStyle
	private val styleSelected: HighlightStyle

	private var ignoreUndoableEvents = false
	private var diffChars: List<DiffChar> = listOf()
	private var filteredText: FilteredText = FilteredText.asIs(FilteredText.Part.of(""))
	private var selection: IntRange? = null

	init {
		textArea.initDocumentFilter(object : DocumentFilter() {
			override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
				val normalized: String = if (textArea.pasting && offset == 0 && length == fb.document.length) {
					textInitializer.initialize(text)
				}
				else {
					text
				}

				super.replace(fb, offset, length, normalized, attrs)
			}
		})

		textArea.setEditable(true)
		textArea.setLineWrap(false)

		styleModified = HighlightStyle({ it.rawBackgroundModified }, { null }, false, false, GapStyle.NONE)
		styleAdded = HighlightStyle({ it.rawBackgroundAdded }, { null }, false, false, GapStyle.NONE)
		styleRemoved = HighlightStyle({ it.rawBackgroundRemoved }, { null }, false, false, GapStyle.NONE)
		styleGapLeft = HighlightStyle({ it.rawBackgroundAddedGap }, { it.rawBackgroundAddedShadow }, false, false, GapStyle.LEFT)
		styleGapRight = HighlightStyle({ it.rawBackgroundAddedGap }, { it.rawBackgroundAddedShadow }, false, false, GapStyle.RIGHT)
		styleFiltered = HighlightStyle({ Color.gray }, { null }, true, false, GapStyle.NONE)
		styleSelected = HighlightStyle({ it.selectionColor }, { null }, false, false, GapStyle.NONE)

		undoManager = UndoManager()

		textArea.document.addUndoableEditListener { e ->
			if (!ignoreUndoableEvents) {
				undoManager.addEdit(e.edit)
			}
		}

		textArea.putAction("Undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), object : AbstractAction("Undo") {
			override fun actionPerformed(evt: ActionEvent?) {
				if (undoManager.canUndo()) {
					undoManager.undo()
				}
			}
		})

		textArea.putAction("Redo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK), object : AbstractAction("Redo") {
			override fun actionPerformed(evt: ActionEvent?) {
				if (undoManager.canRedo()) {
					undoManager.redo()
				}
			}
		})

		textArea.addPropertyChangeListener { evt ->
			if (evt.propertyName == "UI") {
				updateCharacterAttributes()
			}
		}
	}

	fun getText(): String {
		return textArea.text
	}

	fun initializeText(text: String) {
		setText(textInitializer.initialize(text))
	}

	fun setText(text: String) {
		textArea.setText(text)

		undoManager.discardAllEdits()
	}

	fun replaceText(from: Int, to: Int, text: String) {
		textArea.replaceText(from, to, text)
	}

	fun setDiffCharsAndFilteredText(diffChars: List<DiffChar>, filteredText: FilteredText) {
		require(textArea.text.length == diffChars.size)

		this.diffChars = diffChars
		this.filteredText = filteredText

		updateCharacterAttributes()
	}

	fun setSelection(selection: IntRange?) {
		this.selection = selection

		updateCharacterAttributes()
	}

	private fun updateCharacterAttributes() {
		ignoreUndoableEvents = true
		try {
			updateCharacterAttributes(diffChars, ::getHighlighting)
		} finally {
			ignoreUndoableEvents = false
		}
	}

	private fun getHighlighting(index: Int, char: DiffChar): HighlightStyle? {
		if (selection?.contains(index) == true) {
			return styleSelected
		}

		if (filteredText.isFiltered(index)) {
			return styleFiltered
		}

		return when (char.kind) {
			DiffKind.EQUAL -> null
			DiffKind.ADDED -> styleAdded
			DiffKind.MODIFIED -> styleModified
			DiffKind.REMOVED -> styleRemoved
			DiffKind.GAP_LEFT -> styleGapLeft
			DiffKind.GAP_RIGHT -> styleGapRight
		}
	}

	fun interface TextInitializer {
		fun initialize(text: String): String
	}
}