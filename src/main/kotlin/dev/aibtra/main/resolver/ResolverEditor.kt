/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.configuration.*
import dev.aibtra.diff.*
import dev.aibtra.gui.*
import dev.aibtra.main.content.*
import dev.aibtra.main.content.TextArea
import dev.aibtra.resolver.*
import java.awt.*
import javax.swing.text.*

abstract class ResolverEditor(editable: Boolean, private val focusGroup: TextEditorFocusGroup, private val environment: Environment, nameForDebugging: String) :
	AbstractTextEditor(editable, false, environment, nameForDebugging) {
	protected val texter: Texter

	protected abstract fun getHighlighting(char: DiffChar): HighlightStyle?

	private var summary: ResolveSummary? = null

	init {
		textArea.setEditable(true)
		textArea.setLineWrap(false)

		texter = Texter(textArea, environment)

		@Suppress("LeakingThis")
		focusGroup.register(this)
	}

	fun hasFocus(): Boolean {
		return focusGroup.hasFocus(this)
	}

	fun addFocusListener(listen: () -> Unit) {
		focusGroup.addListener(listen)
	}

	protected fun setSummary(summary: ResolveSummary) {
		this.summary = summary

		updateConflictBackgrounds()
		updateCharacterAttributes()
	}

	override fun updateUI() {
		texter.updateHighlights()
		updateConflictBackgrounds()
		updateCharacterAttributes()
	}

	private fun updateConflictBackgrounds() {
		for (highlight in textArea.highlights) {
			if (highlight.painter is ConflictBackgroundPainter) {
				textArea.removeHighlight(highlight)
			}
		}

		summary?.let { summary ->
			for (header in summary.content.headers) {
				header.conflictRange?.let {
					textArea.addHighlight(it.first, it.last, ConflictBackgroundPainter(environment.configurationProvider))
				}
			}
		}
	}

	private fun updateCharacterAttributes() {
		summary?.let {
			updateCharacterAttributes(it.diffChars) { _, char -> getHighlighting(char) }
		}
	}

	class Texter(private val textArea: TextArea, private val environment: Environment) {
		private var snippetToPosition: Map<ResolverSnippet, Position> = mapOf()

		fun initialize(summary: ResolveSummary, overwriteText: Boolean, setText: (text: String, textArea: TextArea) -> Unit): Boolean {
			val content = summary.content
			val snippetsChanged = content.headers.map { it.snippet }.toSet() != snippetToPosition.keys.map { it }.toSet()
			val initialize = snippetsChanged || overwriteText && textArea.text != content.text
			if (initialize) {
				setText(content.text, textArea)

				val snippetToPosition = mutableMapOf<ResolverSnippet, Position>()
				for (header in content.headers) {
					snippetToPosition[header.snippet] = textArea.document.createPosition(header.offset)
				}
				this.snippetToPosition = snippetToPosition
			}

			updateHighlights()
			return initialize
		}

		fun updateHighlights() {
			for (highlight in textArea.highlights) {
				if (highlight.painter is FileHeaderPainter) {
					textArea.removeHighlight(highlight)
				}
			}

			for ((snippet, position) in snippetToPosition) {
				val offset = position.offset
				textArea.addHighlight(offset, offset + 1, FileHeaderPainter(snippet, environment.configurationProvider))
			}
		}

		fun createSummaryContentWithoutConflicts(): ResolverSummaryContent {
			Ui.assertEdt()

			val document = textArea.document
			val text = document.getText(0, document.length)
			val headers = snippetToPosition.map { (snippet, position) ->
				ResolverSummaryContent.Header(snippet, position.offset, IntRange(0, 0), null)
			}.sortedBy { it.offset }
			return ResolverSummaryContent(text, headers)
		}

		fun intersectsFileHeader(offset: Int, length: Int): Boolean {
			for (position in snippetToPosition.values) {
				val headerOffset = position.offset
				if (headerOffset + 1 >= offset && headerOffset <= offset + length) {
					return true
				}
			}

			return false
		}

		private class FileHeaderPainter(val snippet: ResolverSnippet, configurationProvider: ConfigurationProvider) : BackgroundPainter(configurationProvider) {
			override fun paintLayer(g: Graphics, offs0: Int, offs1: Int, bounds: Shape, c: JTextComponent, view: View): Shape {
				val titleBuilder = StringBuilder()
				titleBuilder.append(snippet.file.name)
				snippet.warning?.let {
					titleBuilder.append(" (")
					titleBuilder.append(it)
					titleBuilder.append(")")
				}

				val viewBounds = bounds.bounds
				val shape = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds)
				val shapeBounds = shape.bounds
				with(g as Graphics2D) {
					val oldFont = font
					val oldRenderingHints = renderingHints
					try {
						setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
						font = font.deriveFont(Font.BOLD)
						val guiColors = getGuiColors()
						val (background, foreground) = snippet.warning?.let { Pair(guiColors.resolverWarningHeaderBackgroundColor, c.foreground) } ?: Pair(c.foreground, c.background)
						color = background
						fill(Rectangle(viewBounds.x, shapeBounds.y, viewBounds.width, shapeBounds.height))
						color = foreground
						drawString(titleBuilder.toString(), viewBounds.x, shapeBounds.y + fontMetrics.ascent)
					} finally {
						font = oldFont
						setRenderingHints(oldRenderingHints)
					}
				}
				return shape
			}
		}
	}

	private class ConflictBackgroundPainter(configurationProvider: ConfigurationProvider) : BackgroundPainter(configurationProvider) {
		override fun paintLayer(g: Graphics, offs0: Int, offs1: Int, bounds: Shape, c: JTextComponent, view: View): Shape {
			val viewBounds = bounds.bounds
			val shape = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds)
			val shapeBounds = shape.bounds
			with(g as Graphics2D) {
				color = getGuiColors().resolverUnresolvedConflictBackgroundColor
				fill(Rectangle(viewBounds.x, shapeBounds.y, viewBounds.width, shapeBounds.height))
			}
			return shape
		}
	}

	private open class BackgroundPainter(val configurationProvider: ConfigurationProvider) : DefaultHighlighter.DefaultHighlightPainter(Color.GREEN) {
		protected fun getGuiColors(): GuiColors.Colors {
			return GuiColors.getColors(configurationProvider)
		}
	}
}