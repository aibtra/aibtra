/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.diff.DiffChar
import dev.aibtra.diff.DiffKind
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionAdapter
import javax.swing.JTextArea
import javax.swing.event.CaretListener
import javax.swing.text.*

class RefTextArea(environment: Environment) :
	AbstractTextArea<JTextArea>(JTextArea(), environment) {
	private val styleModified: HighlightStyle
	private val styleAdded: HighlightStyle
	private val styleRemoved: HighlightStyle
	private val styleGapLeft: HighlightStyle
	private val styleGapRight: HighlightStyle
	private val configurationProvider: ConfigurationProvider
	private val documentFilter = NonEditableDocumentFilter()

	private var state: State = State("", listOf())

	init {
		// We are using a JTextArea and Highlighters instead of a JEditorPane/JTextPane, because these have some bugs related to layouting, especially wrapping of lines which are critical for us.
		textArea.lineWrap = true
		textArea.wrapStyleWord = true
		textArea.document.putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n")

		// The JTextArea should be editable, so selection works, but we will prevent actual editing
		(textArea.document as AbstractDocument).documentFilter = documentFilter

		val guiConfiguration = environment.guiConfiguration
		textArea.font = guiConfiguration.fonts.monospacedFont

		styleModified = HighlightStyle({ it.refBackgroundModified }, { null }, false, false, GapStyle.NONE)
		styleAdded = HighlightStyle({ it.refBackgroundAdded }, { null }, false, false, GapStyle.NONE)
		styleRemoved = HighlightStyle({ it.refBackgroundRemoved }, { null }, false, true, GapStyle.NONE)
		styleGapLeft = HighlightStyle({ it.refBackgroundRemoved }, { it.refBackgroundRemovedShadow }, false, false, GapStyle.LEFT)
		styleGapRight = HighlightStyle({ it.refBackgroundRemoved }, { it.refBackgroundRemovedShadow }, false, false, GapStyle.RIGHT)

		textArea.addPropertyChangeListener { evt ->
			if (evt.propertyName == "UI") {
				updateCharacterAttributes()
			}
		}

		configurationProvider = environment.configurationProvider
	}

	fun getSelectionRange(): IntRange? {
		val start = textArea.selectionStart
		val end = textArea.selectionEnd.let {
			val length = textArea.text.length
			if (it <= length) {
				it
			}
			else {
				// Unicode characters which may occupy two Java characters, like U+1F60A, may confuse the selection range.
				// In such cases, it seems that the end index may be one character too large. This can be reproduced when having
				// "Hi!\n\nThank for the test repository and detailed information. " in raw text and
				// "Hi!\n\nThank you for the test repository and the detailed information. U+1F60A" in the refined text and
				// selecting " U+1F60A".
				require(it == length + 1)
				it - 1
			}
		}

		return if (start < end) IntRange(start, end - 1) else null
	}

	fun setText(text: String, chars: List<DiffChar>) {
		require(text.length == chars.size)

		val doc = textArea.document
		val existing = doc.getText(0, doc.length)
		var start = 0
		while (start < existing.length && start < text.length && existing[start] == text[start]) {
			start++
		}

		if (textArea.selectionStart <= start && start < textArea.selectionEnd) { // preserve caret when applying a change
			textArea.caretPosition = start
		}

		documentFilter.locked = false
		try {
			doc.remove(start, existing.length - start)
			doc.insertString(start, text.substring(start), SimpleAttributeSet.EMPTY)
		} finally {
			documentFilter.locked = true
		}

		state = State(text, chars)

		require(text == textArea.text)

		updateCharacterAttributes()
	}

	fun addMouseListener(mouseListener: MouseListener) {
		textArea.addMouseListener(mouseListener)
	}

	private fun updateCharacterAttributes() {
		updateCharacterAttributes(state.diffChars) { _, char -> getHighlighting(char) }
	}

	private fun getHighlighting(char: DiffChar): HighlightStyle? {
		return when (char.kind) {
			DiffKind.EQUAL -> null
			DiffKind.ADDED -> styleAdded
			DiffKind.MODIFIED -> styleModified
			DiffKind.REMOVED -> styleRemoved
			DiffKind.GAP_LEFT -> styleGapLeft
			DiffKind.GAP_RIGHT -> styleGapRight
		}
	}

	fun addSelectionListener(listen: (range: IntRange?) -> Unit) {
		var lastSelectionRange: IntRange? = null
		val notify = fun() {
			val selectionRange: IntRange? = getSelectionRange()
			if (state.text == textArea.text && lastSelectionRange != selectionRange) {
				listen(selectionRange)
				lastSelectionRange = selectionRange
			}
		}

		val caretListener = CaretListener { e ->
			e?.let {
				notify()
			}
		}
		textArea.addCaretListener(caretListener)
		textArea.addMouseMotionListener(object : MouseMotionAdapter() {
			override fun mouseDragged(e: MouseEvent?) {
				notify()
			}
		})
	}

	private data class State(val text: String, val diffChars: List<DiffChar>)

	private class NonEditableDocumentFilter : DocumentFilter() {
		var locked = true

		override fun insertString(fb: FilterBypass?, offset: Int, string: String?, attr: AttributeSet?) {
			if (locked) {
				beep()
				return
			}

			super.insertString(fb, offset, string, attr)
		}

		override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attrs: AttributeSet?) {
			if (locked) {
				beep()
				return
			}

			super.replace(fb, offset, length, text, attrs)
		}

		override fun remove(fb: FilterBypass?, offset: Int, length: Int) {
			if (locked) {
				beep()
				return
			}

			super.remove(fb, offset, length)
		}

		private fun beep() {
			Toolkit.getDefaultToolkit().beep()
		}
	}
}