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
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.event.CaretListener
import javax.swing.text.*

class RefTextArea(environment: Environment) {
	private val textArea: JTextArea
	private val hightlighter: Highlighter
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
		textArea = JTextArea()
		textArea.lineWrap = true
		textArea.wrapStyleWord = true
		textArea.document.putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n")

		// The JTextArea should be editable, so selection works, but we will prevent actual editing
		(textArea.document as AbstractDocument).documentFilter = documentFilter

		val guiConfiguration = environment.guiConfiguration
		textArea.font = guiConfiguration.fonts.monospacedFont

		hightlighter = Highlighter(textArea, environment.configurationProvider)

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

	fun createControl(): Component {
		return JScrollPane(textArea)
	}

	fun addMouseListener(mouseListener: MouseListener) {
		textArea.addMouseListener(mouseListener)
	}

	private fun updateCharacterAttributes() {
		hightlighter.run(state.diffChars) { _, char -> getHighlighting(char) }
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

	enum class GapStyle {
		NONE, LEFT, RIGHT
	}

	class HighlightStyle(val color: (GuiColors.Colors) -> Color, val shadow: (GuiColors.Colors) -> Color?, val sprinkled: Boolean, val strikethrough: Boolean, val gapStyle: GapStyle)

	class Highlighter(private val textArea: JTextArea, private val configurationProvider: ConfigurationProvider) {
		private val highlightStyleToPainter = mutableMapOf<HighlightStyle, DefaultHighlighter.DefaultHighlightPainter>()
		private val ourHighlightTags = HashSet<Any>()

		fun run(chars: List<DiffChar>, highlightStyle: (index: Int, char: DiffChar) -> HighlightStyle?) {
			val configuration = configurationProvider.get(GuiConfiguration)
			val guiColors = configurationProvider.get(GuiColors)
			val colors = if (configuration.darkTheme) {
				guiColors.dark
			}
			else {
				guiColors.light
			}

			for (tag in ourHighlightTags) {
				textArea.highlighter.removeHighlight(tag)
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
				val tag = textArea.highlighter.addHighlight(from, to, highlightStyleToPainter.computeIfAbsent(style) {
					Painter(it, it.color(colors), it.shadow(colors), textArea.foreground, textArea.background)
				})

				ourHighlightTags.add(tag)
			}
		}

		private class Painter(val style: HighlightStyle, highlightColor: Color, val shadowColor: Color?, val foreground: Color, background: Color) : DefaultHighlighter.DefaultHighlightPainter(highlightColor) {
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