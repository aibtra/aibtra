/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.configuration.*
import dev.aibtra.diff.*
import dev.aibtra.main.content.*
import javax.swing.text.*

class RefinerRefEditor(environment: Environment) :
	AbstractTextEditor(false, environment) {
	private val styleModified: HighlightStyle
	private val styleAdded: HighlightStyle
	private val styleRemoved: HighlightStyle
	private val styleGapLeft: HighlightStyle
	private val styleGapRight: HighlightStyle
	private val configurationProvider: ConfigurationProvider

	private var state: State = State(listOf())

	init {
		// We are using a JTextArea and Highlighters instead of a JEditorPane/JTextPane, because these have some bugs related to layouting, especially wrapping of lines which are critical for us.
		textArea.setLineWrap(false)
		textArea.document.putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n")

		styleModified = HighlightStyle({ it.refBackgroundModified }, { null }, false, false, GapStyle.NONE)
		styleAdded = HighlightStyle({ it.refBackgroundAdded }, { null }, false, false, GapStyle.NONE)
		styleRemoved = HighlightStyle({ it.refBackgroundRemoved }, { null }, false, true, GapStyle.NONE)
		styleGapLeft = HighlightStyle({ it.refBackgroundRemovedGap }, { it.refBackgroundRemovedShadow }, false, false, GapStyle.LEFT)
		styleGapRight = HighlightStyle({ it.refBackgroundRemovedGap }, { it.refBackgroundRemovedShadow }, false, false, GapStyle.RIGHT)

		textArea.addPropertyChangeListener { evt ->
			if (evt.propertyName == "UI") {
				updateCharacterAttributes()
			}
		}

		configurationProvider = environment.configurationProvider
	}

	fun setText(text: String, chars: List<DiffChar>, preserveCaretPosition: Boolean) {
		require(text.length == chars.size)

		val doc = textArea.document
		val existing = doc.getText(0, doc.length)
		var start = 0
		while (start < existing.length && start < text.length && existing[start] == text[start]) {
			start++
		}

		textArea.setText(text, if (preserveCaretPosition) textArea.caretPosition else start)

		state = State(chars)

		require(text == textArea.text)

		updateCharacterAttributes()
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

	private data class State(val diffChars: List<DiffChar>)
}