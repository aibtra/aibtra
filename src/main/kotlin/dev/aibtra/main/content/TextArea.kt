package dev.aibtra.main.content

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.gui.*
import org.fife.ui.rsyntaxtextarea.*
import org.fife.ui.rtextarea.*
import java.awt.*
import java.awt.event.*
import java.awt.geom.*
import java.beans.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.*
import kotlin.math.*

class TextArea(private val editable: Boolean, private val syntaxSupport: Boolean, configurationProvider: ConfigurationProvider) {

	var pasting = false
		private set

	private val textArea = object : RSyntaxTextArea() {
		init {
			highlightCurrentLine = false
			animateBracketMatching = false
			isBracketMatchingEnabled = false
		}

		override fun paste() {
			pasting = true
			try {
				super.paste()
			} finally {
				pasting = false
			}
		}
	}

	private val documentFilter: NonEditableDocumentFilter? = if (!editable) {
		val filter = NonEditableDocumentFilter()
		(textArea.document as AbstractDocument).documentFilter = filter
		filter
	}
	else {
		null
	}

	private val scrollPane = object : RTextScrollPane(textArea) {
		init {
			Theme.applyRSyntaxTextTheme(this, configurationProvider)
		}
	}

	val text: String
		get() = textArea.text
	val lineCount: Int
		get() = textArea.lineCount
	val document: Document
		get() = textArea.document as AbstractDocument
	val foreground: Color
		get() = textArea.foreground
	val background: Color
		get() = textArea.background
	val visibleRect: Rectangle
		get() = textArea.visibleRect

	val height: Int
		get() = textArea.height
	val selectedText: String
		get() = textArea.selectedText
	val selectionStart: Int
		get() = textArea.selectionStart
	val selectionEnd: Int
		get() = textArea.selectionEnd
	val caretPosition: Int
		get() = textArea.caretPosition

	val highlights: Array<Highlighter.Highlight>
		get() = textArea.highlighter.highlights

	init {
		textArea.wrapStyleWord = true
	}

	fun initDocumentFilter(documentFilter: DocumentFilter) {
		(textArea.document as AbstractDocument).documentFilter = documentFilter
	}

	fun getScrollPane(): JScrollPane {
		return scrollPane
	}

	fun setText(text: String, syntaxType: SyntaxType, caretPosition: Int = 0) {
		require(syntaxSupport || syntaxType == SyntaxType.NONE)

		if (editable) {
			textArea.text = text
			textArea.caretPosition = caretPosition
		}
		else {
			val doc = textArea.document
			val existing = doc.getText(0, doc.length)
			var start = 0
			while (start < existing.length && start < text.length && existing[start] == text[start]) {
				start++
			}

			if (textArea.selectionStart <= start && start < textArea.selectionEnd) {
				textArea.setCaretPosition(start)
			}

			documentFilter?.update {
				doc.remove(start, existing.length - start)
				doc.insertString(start, text.substring(start), SimpleAttributeSet.EMPTY)
			}

			textArea.setCaretPosition(max(0, min(text.length - 1, caretPosition)))
		}

		textArea.syntaxEditingStyle = syntaxType.internal
	}

	fun replaceText(from: Int, to: Int, text: String) {
		textArea.document.remove(from, to - from)
		textArea.document.insertString(from, text, null)
		textArea.caretPosition = from
	}

	fun addHighlight(p0: Int, p1: Int, p: Highlighter.HighlightPainter): Highlighter.Highlight {
		return textArea.highlighter.addHighlight(p0, p1, p) as Highlighter.Highlight
	}

	fun removeHighlight(tag: Any) {
		textArea.highlighter.removeHighlight(tag)
	}

	fun addPropertyChangeListener(listener: PropertyChangeListener) {
		textArea.addPropertyChangeListener(listener)
	}

	fun requestFocusInWindow() {
		textArea.requestFocusInWindow()
	}

	fun scrollRectToVisible(rectangle: Rectangle) {
		textArea.scrollRectToVisible(rectangle)
	}

	fun modelToView2D(pos: Int): Rectangle2D {
		return textArea.modelToView2D(pos)
	}

	fun getLineStartOffset(line: Int): Int {
		return textArea.getLineStartOffset(line)
	}

	fun addCaretListener(listener: CaretListener) {
		textArea.addCaretListener(listener)
	}

	fun addMouseMotionListener(mouseMotionListener: MouseMotionListener) {
		textArea.addMouseMotionListener(mouseMotionListener)
	}

	fun addMouseListener(mouseListener: MouseListener) {
		textArea.addMouseListener(mouseListener)
	}

	fun viewToModel2D(point: Point2D): Int {
		return textArea.viewToModel2D(point)
	}

	fun setLineWrap(lineWrap: Boolean) {
		textArea.lineWrap = lineWrap
	}

	fun addFocusListener(focusListener: FocusListener) {
		textArea.addFocusListener(focusListener)
	}

	fun setEditable(editable: Boolean) {
		textArea.isEditable = editable
	}

	fun setFont(font: Font) {
		textArea.font = font
	}

	fun putAction(key: String, keyStroke: KeyStroke, action: Action) {
		textArea.actionMap.put(key, action)
		textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, key)
	}

	fun addSelectionListener(listener: (Pair<Int, Int>?) -> Unit) {
		var lastSelection: Pair<Int, Int>? = null
		val check = {
			val selection = if (selectionEnd > selectionStart) Pair(textArea.selectionStart, textArea.selectionEnd) else null
			if (selection != lastSelection) {
				Ui.runInEdt {
					// Needs to be delayed; otherwise, when selecting the first character, the selection highlight gets lost.
					listener(selection)
				}
				lastSelection = selection
			}
		}

		textArea.addCaretListener {
			check()
		}
		textArea.addKeyListener(object: KeyAdapter() {
			override fun keyReleased(e: KeyEvent?) {
				check()
			}
		})
	}

	private class NonEditableDocumentFilter : DocumentFilter() {
		private var locked = true

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

		fun update(runnable: Runnable) {
			locked = false
			try {
				runnable.run()
			} finally {
				locked = true
			}
		}
	}
}