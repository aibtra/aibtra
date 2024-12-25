/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.content

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.diff.*
import java.awt.*
import java.awt.event.*
import java.awt.geom.*
import java.awt.image.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.*
import kotlin.math.*

open class AbstractTextEditor(editable: Boolean, environment: Environment) {
	private val LOG = Logger.getLogger(this::class)

	protected val textArea = TextArea(editable)
	private val scrollPane = textArea.createScrollPane()
	private val configurationProvider = environment.configurationProvider
	private val highlighter = Highlighter(textArea, configurationProvider)

	init {
		val guiConfiguration = environment.guiConfiguration
		textArea.setFont(guiConfiguration.fonts.monospacedFont)

		textArea.addPropertyChangeListener { evt ->
			if (evt.propertyName == "UI") {
				highlighter.resetPainters()
			}
		}
	}

	fun getControl(): Component {
		return scrollPane
	}

	fun requestFocusInWindow() {
		textArea.requestFocusInWindow()
	}

	protected fun updateCharacterAttributes(chars: List<DiffChar>, highlightStyle: (index: Int, char: DiffChar) -> HighlightStyle?) {
		highlighter.run(chars, highlightStyle)
	}

	fun scrollTo(pos: ScrollState.ScrollPos, mode: ScrollState.ScrollMode) {
		if (pos == createScrollPos()) {
			return
		}

		if (pos.top == 0) {
			textArea.scrollRectToVisible(Rectangle(0, 0, 0, 0))
			return
		}

		if (pos.bottom >= textArea.text.length - 1) {
			val height = textArea.visibleRect.height
			textArea.scrollRectToVisible(Rectangle(0, textArea.height - height, 0, height))
			return
		}

		val top = textArea.modelToView2D(pos.top)
		val bottom = textArea.modelToView2D(pos.bottom)
		val topBounds = top.bounds
		val bottomBounds = bottom.bounds
		val scrollTop: Int
		val scrollBottom: Int
		val viewportHeight = scrollPane.viewport.height
		if (mode == ScrollState.ScrollMode.FORCE_TOP) {
			scrollTop = topBounds.y
			scrollBottom = min(bottomBounds.y + bottomBounds.height - topBounds.y, scrollTop + viewportHeight)
		}
		else {
			scrollBottom = bottomBounds.y + bottomBounds.height - topBounds.y
			scrollTop = max(topBounds.y, scrollBottom - viewportHeight)
		}

		textArea.scrollRectToVisible(Rectangle(0, scrollTop, 0, scrollBottom))
	}

	fun scrollToLine(line: Int) {
		if (line < 0 || line >= textArea.lineCount) {
			return
		}

		try {
			val startOffset = textArea.getLineStartOffset(line)
			val rectangle = textArea.modelToView2D(startOffset)
			textArea.scrollRectToVisible(rectangle.bounds)
		} catch (ex: BadLocationException) {
			LOG.error(ex)
		}
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

	fun addCaretListener(listen: (pos: Int) -> Unit) {
		textArea.addCaretListener { e ->
			e?.let {
				listen(textArea.caretPosition)
			}
		}
	}

	fun addSelectionListener(listen: (range: IntRange?) -> Unit) {
		var currentText = ""
		textArea.document.addDocumentListener(object : DocumentListener {
			override fun insertUpdate(e: DocumentEvent) {
				currentText = textArea.text
			}

			override fun removeUpdate(e: DocumentEvent) {
				currentText = textArea.text
			}

			override fun changedUpdate(e: DocumentEvent) {
				currentText = textArea.text
			}
		})

		var lastSelectionRange: IntRange? = null
		val notify = fun() {
			val selectionRange: IntRange? = getSelectionRange()
			if (currentText == textArea.text && lastSelectionRange != selectionRange) {
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

	fun addPopupMenu(leftMouseButton: Boolean = false, fill: (pos: Int, popupMenu: JPopupMenu) -> Unit) {
		textArea.addMouseListener(object : MouseAdapter() {
			override fun mouseReleased(e: MouseEvent) {
				if (!(isPopupButton(e))) {
					return
				}

				val pos = textArea.viewToModel2D(e.point)
				JPopupMenu().apply {
					fill(pos, this)
					if (componentCount > 0) {
						show(e.component, e.x, e.y)
					}
				}
			}

			private fun isPopupButton(e: MouseEvent): Boolean {
				return leftMouseButton && SwingUtilities.isLeftMouseButton(e) || !leftMouseButton && SwingUtilities.isRightMouseButton(e)
			}
		})
	}

	fun getSelectionText(): String {
		return textArea.selectedText
	}

	fun getCaretPosition(): Int {
		return textArea.caretPosition
	}

	fun getSelectionRange(): IntRange? {
		val start = textArea.selectionStart
		val end = textArea.selectionEnd.let {
			val length = textArea.text.length
			if (it <= length) {
				it
			}
			else if (it == length + 1) {
				// Unicode characters which may occupy two Java characters, like U+1F60A, may confuse the selection range.
				// In such cases, it seems that the end index may be one character too large. This can be reproduced when having
				// "Hi!\n\nThank for the test repository and detailed information. " in raw text and
				// "Hi!\n\nThank you for the test repository and the detailed information. U+1F60A" in the refined text and
				// selecting " U+1F60A".
				it - 1
			}
			else {
				-1
			}
		}

		return if (start < end) IntRange(start, end - 1) else null
	}

	fun addScrollListener(callback: (pos: ScrollState.ScrollPos, mode: ScrollState.ScrollMode?) -> Unit) {
		scrollPane.viewport.addChangeListener {
			callback(createScrollPos(), null)
		}
		scrollPane.addMouseWheelListener {
			val viewport = scrollPane.viewport
			val viewPosition: Point = viewport.viewPosition
			val viewportSize = viewport.size
			if (viewPosition.y <= 0) {
				callback(createScrollPos(), ScrollState.ScrollMode.FORCE_TOP)
			}
			if (viewPosition.y + viewportSize.height >= viewport.view.size.height) {
				callback(createScrollPos(), ScrollState.ScrollMode.FORCE_BOTTOM)
			}
		}
	}

	fun setWordWrap(wordWrap: Boolean) {
		textArea.setLineWrap(wordWrap)
	}

	fun addFocusListener(focusListener: FocusListener) {
		textArea.addFocusListener(focusListener)
	}

	private fun createScrollPos(): ScrollState.ScrollPos {
		val rect = scrollPane.viewport.viewRect
		val topModel = textArea.viewToModel2D(Point2D.Double(rect.x.toDouble(), rect.y.toDouble()))
		val bottomModel = textArea.viewToModel2D(Point2D.Double(rect.x.toDouble(), (rect.y + rect.height).toDouble()))
		if (topModel < 0 || bottomModel < 0) {
			return ScrollState.ScrollPos(0, 0)
		}

		return ScrollState.ScrollPos(topModel, bottomModel)
	}

	private class Highlighter(private val textArea: TextArea, private val configurationProvider: ConfigurationProvider) {
		private val highlightStyleToPainter = mutableMapOf<HighlightStyle, DefaultHighlighter.DefaultHighlightPainter>()
		private val ourHighlightTags = HashSet<Any>()

		fun run(chars: List<DiffChar>, highlightStyle: (index: Int, char: DiffChar) -> HighlightStyle?) {
			val colors = GuiColors.getColors(configurationProvider)
			for (tag in ourHighlightTags) {
				textArea.removeHighlight(tag)
			}
			ourHighlightTags.clear()

			var lastStart = -1
			var last: HighlightStyle? = null
			for ((index, char) in chars.withIndex()) {
				highlightStyle(index, char).let {
					if (it != last) {
						addHighlight(lastStart, index, last, colors)
						lastStart = index
					}

					last = it
				}
			}

			addHighlight(lastStart, chars.size, last, colors)
		}

		fun resetPainters() {
			highlightStyleToPainter.clear()
		}

		private fun addHighlight(from: Int, to: Int, highlightStyle: HighlightStyle?, colors: GuiColors.Colors) {
			highlightStyle?.let { style ->
				val highlight: DefaultHighlighter.DefaultHighlightPainter = highlightStyleToPainter.computeIfAbsent(style) {
					HighlightPainter(it, it.color(colors), it.shadow(colors), textArea.foreground, textArea.background)
				}
				val tag = textArea.addHighlight(from, to, highlight)
				ourHighlightTags.add(tag)
			}
		}
	}

	enum class GapStyle {
		NONE, LEFT, RIGHT
	}

	class HighlightStyle(val color: (GuiColors.Colors) -> Color, val shadow: (GuiColors.Colors) -> Color?, val sprinkled: Boolean, val strikethrough: Boolean, val gapStyle: GapStyle)

	protected class HighlightPainter(private val style: HighlightStyle, highlightColor: Color, private val shadowColor: Color?, private val foreground: Color, background: Color) : DefaultHighlighter.DefaultHighlightPainter(highlightColor) {
		private val texturePaint = if (style.sprinkled) createSprinkledTexturePaint(highlightColor, background) else null

		override fun paintLayer(g: Graphics, offs0: Int, offs1: Int, bounds: Shape, c: JTextComponent, view: View): Shape {
			val gap = style.gapStyle != GapStyle.NONE
							&& (offs0 == offs1 || offs0 == offs1 - 1)
			val shape = if (gap) {
				view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds)
			}
			else {
				super.paintLayer(g, offs0, offs1, bounds, c, view)
			}

			texturePaint?.let { texturePaint ->
				with(g as Graphics2D) {
					val lastPaint = paint
					try {
						paint = texturePaint
						fill(shape)
					} finally {
						paint = lastPaint
					}
				}
			}

			if (gap) {
				val r = if (shape is Rectangle) shape else shape.bounds
				val oldColor = g.color

				shadowColor?.let {
					g.color = it
					(g as Graphics2D).fill(shape)
				}

				g.color = color

				@Suppress("KotlinConstantConditions")
				when (style.gapStyle) {
					GapStyle.LEFT -> g.fillRect(r.x + r.width - 1, r.y, 1, r.height)
					GapStyle.RIGHT -> g.fillRect(r.x, r.y, 1, r.height)
					GapStyle.NONE -> require(false)
				}
				g.color = oldColor
			}

			if (style.strikethrough) {
				(shape as? Rectangle)?.let {
					val yCenter = it.y + it.height / 2
					g.color = foreground
					g.drawLine(it.x, yCenter, it.x + it.width, it.y + it.height / 2)
				}
			}
			return shape
		}

		private fun createSprinkledTexturePaint(color: Color, background: Color): TexturePaint {
			val textureImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
			val textureGraphics = textureImage.createGraphics()
			textureGraphics.color = background
			textureGraphics.fillRect(0, 0, 2, 2)
			textureGraphics.color = color
			textureGraphics.fillRect(0, 0, 1, 1)
			textureGraphics.fillRect(1, 1, 1, 1)
			return TexturePaint(textureImage, Rectangle2D.Double(0.0, 0.0, 2.0, 2.0))
		}
	}
}