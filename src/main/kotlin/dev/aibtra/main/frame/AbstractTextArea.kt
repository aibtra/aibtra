/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.diff.DiffChar
import dev.aibtra.diff.DiffManager
import java.awt.*
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.text.DefaultHighlighter
import javax.swing.text.JTextComponent
import javax.swing.text.Position
import javax.swing.text.View

open class AbstractTextArea<T : JTextArea>(protected val textArea: T, environment: Environment) {
	private val scrollPane = JScrollPane(textArea)
	private val configurationProvider = environment.configurationProvider
	private val highlighter = Highlighter(textArea, configurationProvider)

	init {
		textArea.addPropertyChangeListener { evt ->
			if (evt.propertyName == "UI") {
				highlighter.resetPainters()
			}
		}
	}

	fun getControl(): Component {
		return scrollPane
	}

	protected fun updateCharacterAttributes(chars: List<DiffChar>, highlightStyle: (index: Int, char: DiffChar) -> HighlightStyle?) {
		highlighter.run(chars, highlightStyle)
	}

	fun scrollTo(pos: DiffManager.ScrollPos) {
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
		if (top == null || bottom == null) {
			return
		}

		val topBounds = top.bounds
		val bottomBounds = bottom.bounds
		textArea.scrollRectToVisible(Rectangle(topBounds.x, topBounds.y, 0, bottomBounds.y + bottomBounds.height - topBounds.y))
	}

	fun addScrollListener(callback: (pos: DiffManager.ScrollPos) -> Unit) {
		scrollPane.viewport.addChangeListener {
			callback(createScrollPos())
		}
	}

	private fun createScrollPos(): DiffManager.ScrollPos {
		val rect = scrollPane.viewport.viewRect
		val topModel = textArea.viewToModel2D(Point2D.Double(rect.x.toDouble(), rect.y.toDouble()))
		val bottomModel = textArea.viewToModel2D(Point2D.Double(rect.x.toDouble(), (rect.y + rect.height).toDouble()))
		if (topModel < 0 || bottomModel < 0) {
			return DiffManager.ScrollPos(0, 0)
		}

		return DiffManager.ScrollPos(topModel, bottomModel)
	}

	private class Highlighter(private val textArea: JTextArea, private val configurationProvider: ConfigurationProvider) {
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

	enum class GapStyle {
		NONE, LEFT, RIGHT
	}

	class HighlightStyle(val color: (GuiColors.Colors) -> Color, val shadow: (GuiColors.Colors) -> Color?, val sprinkled: Boolean, val strikethrough: Boolean, val gapStyle: GapStyle)
}