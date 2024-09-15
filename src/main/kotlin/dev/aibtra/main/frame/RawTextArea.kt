/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.diff.DiffChar
import dev.aibtra.diff.DiffKind
import dev.aibtra.text.FilteredText
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import javax.swing.undo.UndoManager

class RawTextArea(private val textInitializer: TextInitializer, environment: Environment) {
	private val textArea: JTextArea
	private val highlighter: RefTextArea.Highlighter
	private val undoManager: UndoManager
	private val styleModified: RefTextArea.HighlightStyle
	private val styleAdded: RefTextArea.HighlightStyle
	private val styleRemoved: RefTextArea.HighlightStyle
	private val styleGapLeft: RefTextArea.HighlightStyle
	private val styleGapRight: RefTextArea.HighlightStyle
	private val styleFiltered: RefTextArea.HighlightStyle
	private val configurationProvider = environment.configurationProvider

	private var ignoreUndoableEvents = false
	private var diffChars: List<DiffChar> = listOf()
	private var filteredText: FilteredText = FilteredText("", emptyMap(), setOf())

	init {
		var pasting = false
		textArea = object : JTextArea() {
			override fun paste() {
				pasting = true
				try {
					super.paste()
				} finally {
					pasting = false
				}
			}
		}

		(textArea.document as AbstractDocument).let {
			it.documentFilter = object : DocumentFilter() {
				override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
					val normalized: String = if (pasting && offset == 0 && length == fb.document.length) {
						textInitializer.initialize(text)
					}
					else {
						text
					}

					super.replace(fb, offset, length, normalized, attrs)
				}
			}
		}

		textArea.isEditable = true
		textArea.lineWrap = true
		textArea.wrapStyleWord = true

		val guiConfiguration = environment.guiConfiguration
		textArea.font = guiConfiguration.fonts.monospacedFont

		styleModified = RefTextArea.HighlightStyle({ it.rawBackgroundModified }, { null }, false, false, RefTextArea.GapStyle.NONE)
		styleAdded = RefTextArea.HighlightStyle({ it.rawBackgroundAdded }, { null }, false, true, RefTextArea.GapStyle.NONE)
		styleRemoved = RefTextArea.HighlightStyle({ it.rawBackgroundRemoved }, { null }, false, false, RefTextArea.GapStyle.NONE)
		styleGapLeft = RefTextArea.HighlightStyle({ it.rawBackgroundRemoved }, { it.rawBackgroundRemovedShadow }, false, false, RefTextArea.GapStyle.LEFT)
		styleGapRight = RefTextArea.HighlightStyle({ it.rawBackgroundRemoved }, { it.rawBackgroundRemovedShadow }, false, false, RefTextArea.GapStyle.RIGHT)
		styleFiltered = RefTextArea.HighlightStyle({ Color.gray }, { null }, true, false, RefTextArea.GapStyle.NONE)

		highlighter = RefTextArea.Highlighter(textArea, configurationProvider)
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

		textArea.addPropertyChangeListener { evt ->
			if (evt.propertyName == "UI") {
				highlighter.resetPainters()
				updateCharacterAttributes()
			}
		}

		val inputMap = textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK), "Redo")
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "Undo")
	}

	fun getText(): String {
		return textArea.text
	}

	fun setText(text: String) {
		val initialize = textInitializer.initialize(text)
		textArea.text = initialize
		textArea.caretPosition = 0

		undoManager.discardAllEdits()
	}

	fun replaceText(from: Int, to: Int, text: String) {
		textArea.document.remove(from, to - from)
		textArea.document.insertString(from, text, null)
	}

	fun setDiffCharsAndFilteredText(diffChars: List<DiffChar>, filteredText: FilteredText) {
		require(textArea.text.length == diffChars.size)

		this.diffChars = diffChars
		this.filteredText = filteredText

		updateCharacterAttributes()
	}

	fun createControl(): Component {
		return JScrollPane(textArea)
	}

	fun addContentListener(listen: () -> Unit) {
		textArea.document.addDocumentListener(object : DocumentListener {
			override fun insertUpdate(e: DocumentEvent) {
				listen()
			}

			override fun removeUpdate(e: DocumentEvent) {
				listen()
			}

			override fun changedUpdate(e: DocumentEvent) {
				listen()
			}
		})
	}

	private fun updateCharacterAttributes() {
		ignoreUndoableEvents = true
		try {
			highlighter.run(diffChars, ::getHighlighting)
		} finally {
			ignoreUndoableEvents = false
		}
	}

	private fun getHighlighting(index: Int, char: DiffChar): RefTextArea.HighlightStyle? {
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