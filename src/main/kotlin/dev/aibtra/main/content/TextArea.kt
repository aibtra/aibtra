package dev.aibtra.main.content

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.gui.*
import org.fife.ui.rsyntaxtextarea.*
import org.fife.ui.rtextarea.*
import java.awt.*
import java.awt.datatransfer.*
import java.awt.event.*
import java.awt.geom.*
import java.beans.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.*
import kotlin.math.*

class TextArea(private val editable: Boolean, private val syntaxSupport: Boolean, configurationProvider: ConfigurationProvider, nameForDebugging: String) {

	var pasting = false
		private set

	private val textArea = object : RSyntaxTextArea() {
		init {
			highlightCurrentLine = false
			animateBracketMatching = false
			isBracketMatchingEnabled = false

			name = nameForDebugging
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

	fun setText(text: String, syntaxType: SyntaxType, caretPosition: Int? = null) {
		require(syntaxSupport || syntaxType == SyntaxType.NONE)

		if (editable) {
			textArea.text = text
			textArea.caretPosition = caretPosition ?: 0
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

			caretPosition?.let {
				textArea.setCaretPosition(max(0, min(text.length - 1, it)))
			}
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

	fun setCaretPosition(pos: Int) {
		textArea.caretPosition = pos
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

	fun copy() {
		textArea.copy()
	}

	fun copySelectionToClipboard() {
		val selection = StringSelection(textArea.selectedText)
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		clipboard.setContents(selection, selection)
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

	class ActiveRange(val textArea: RSyntaxTextArea, private val gutter: Gutter, val theme: Theme) {
		companion object {
			fun install(scrollPaneRaw: JScrollPane, theme: Theme): ActiveRange {
				val scrollPane = scrollPaneRaw as RTextScrollPane
				val textArea = scrollPane.textArea as RSyntaxTextArea
				val gutter = scrollPane.gutter
				gutter.isIconRowHeaderEnabled = true
				scrollPane.lineNumbersEnabled = true

				val components = gutter.components
				require(components.size == 2)

				val activeRange = ActiveRange(textArea, gutter, theme)
				val adapter = object : MouseAdapter() {
					private var pressedLine: Int? = null
					private var startLine: Int? = null
					private var endLine: Int? = null

					override fun mousePressed(e: MouseEvent) {
						getLineFromMouseEvent(e)?.let { line ->
							pressedLine = line
							startLine = null
							endLine = null
							updateLineRange(textArea, null)
						}
					}

					override fun mouseReleased(e: MouseEvent) {
						getLineFromMouseEvent(e)?.let { line ->
							startLine?.let { start ->
								endLine = line
								updateLineRange(textArea, IntRange(start, line))
							}
						}
					}

					override fun mouseDragged(e: MouseEvent) {
						getLineFromMouseEvent(e)?.let { line ->
							pressedLine?.let {
								if (line == it) {
									this.startLine = line
								}

								val startLine = startLine ?: line
								this.endLine = line
								updateLineRange(textArea, IntRange(startLine, line))
							}
						}
					}

					private fun getLineFromMouseEvent(e: MouseEvent): Int? {
						return try {
							val pos = textArea.viewToModel2D(e.point)
							val line = textArea.getLineOfOffset(pos)
							if (line >= 0 && line < textArea.lineCount) line else null
						} catch (ex: Exception) {
							null
						}
					}

					private fun updateLineRange(textArea: RSyntaxTextArea, lineRange: IntRange?) {
						lineRange?.let {
							val startLine = min(it.first, it.last)
							val endLine = max(it.first, it.last)
							try {
								val startOffset = textArea.getLineStartOffset(startLine)
								val endOffset = textArea.getLineEndOffset(endLine) - 1
								activeRange.update(IntRange(startOffset, endOffset))
							} catch (_: BadLocationException) {
							}
						} ?: run {
							activeRange.update(null)
						}
					}
				}

				for (component in components) {
					component.addMouseListener(adapter)
					component.addMouseMotionListener(adapter)
				}

				theme.addChangeListener {
					activeRange.refresh()
				}

				activeRange.refresh()
				return activeRange
			}
		}

		private val listeners = mutableListOf<(IntRange?) -> Unit>()

		var range: IntRange? = null
			private set

		fun addListener(listener: (IntRange?) -> Unit) {
			listeners.add(listener)
		}

		fun update(range: IntRange?) {
			require(range == null || range.start <= range.endInclusive)

			this.range = range

			refresh()

			listeners.forEach { it(range) }
		}

		private fun refresh() {
			val colors = GuiColors.getColors(theme.configurationProvider)
			gutter.activeLineRangeColor = colors.activeRangeIntense
			range?.let {
				val activeColor = colors.activeRangeSoft
				val fromLine = textArea.getLineOfOffset(it.first)
				val toLine = textArea.getLineOfOffset(it.last)
				textArea.setActiveLineRange(fromLine, toLine)

				textArea.removeAllLineHighlights()
				for (line in fromLine..toLine) {
					textArea.addLineHighlight(line, activeColor)
				}
			} ?: run {
				textArea.setActiveLineRange(-1, -1)
				textArea.removeAllLineHighlights()
			}
		}
	}
}