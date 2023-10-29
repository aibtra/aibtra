/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.text

import kotlinx.serialization.Serializable

class TextNormalizer(val config: Config) {
	fun normalize(text: String): String {
		var normalized = normalizeEols(text)
		if (config.fixMissingEmptyLineAfterBlockQuote) {
			normalized = fixMissingEmptyLineAfterBlockQuote(normalized, config.changeDoubleToSingleBlockQuotes)
		}
		if (config.joinLines) {
			normalized = joinLines(normalized)
		}
		if (config.changeDoubleToSingleBlockQuotes) {
			normalized = changeDoubleToSingleBlockQuotes(normalized)
		}
		if (config.rewrapBlockQuotes) {
			normalized = rewrapBlockQuotes(normalized, config.rewrapBlockQuotesAt)
		}
		return normalized
	}

	private fun normalizeEols(text: String): String {
		return text.replace("\r\n", "\n").replace("\r", "\n")
	}

	private fun fixMissingEmptyLineAfterBlockQuote(text: String, fixDoubleBlockQuotes: Boolean): String {
		val normalized = StringBuilder()
		val lines = text.split("\n")
		var blockQuote = false
		for ((index, line) in lines.withIndex()) {
			@Suppress("LiftReturnOrAssignment")
			if (line.startsWith("> ") ||
				fixDoubleBlockQuotes && line.startsWith(">> ")
			) {
				blockQuote = true
			}
			else {
				if (line.isNotEmpty() && blockQuote && !normalized.endsWith("\n\n")) {
					normalized.append("\n")
				}

				blockQuote = false
			}

			normalized.append(line)
			if (index < lines.size - 1) {
				normalized.append("\n")
			}
		}
		return normalized.toString()
	}

	private fun joinLines(text: String): String {
		val filtered = FilteredText.filter(text)
		val clean = filtered.clean.split("\n")
		var forceNewline = false

		val normalized = StringBuilder()
		for (line in clean) {
			if (forceNewline) {
				normalized.append("\n")
			}

			val trimmed = line.trim()
			val controlLine = trimmed.startsWith(">") ||
					trimmed.startsWith("```") ||
					trimmed.startsWith("- ") ||
					trimmed.startsWith("* ") ||
					trimmed.startsWith("+ ") ||
					trimmed.startsWith("#") ||
					trimmed.matches(Regex("\\d+\\. .*"))

			forceNewline = if (
				controlLine) {
				true
			}
			else {
				line.isBlank()
			}

			if (forceNewline &&
				normalized.isNotEmpty() &&
				!normalized.endsWith('\n')
			) {
				normalized.append("\n")
			}
			else if (normalized.isNotEmpty() && !normalized.endsWith('\n')) {
				normalized.append(" ")
			}

			if (controlLine) {
				normalized.append(line)
			}
			else {
				normalized.append(trimmed)
			}
		}

		val result = filtered.recreate(normalized.toString())
		require(result.isSuccess)
		return result.getOrThrow()
	}

	private fun changeDoubleToSingleBlockQuotes(text: String): String {
		val normalized = StringBuilder()
		val lines = text.split("\n")
		for ((index, line) in lines.withIndex()) {
			normalized.append(
				if (line.startsWith(">> ")) {
					line.substring(1)
				}
				else {
					line
				}
			)
			if (index < lines.size - 1) {
				normalized.append("\n")
			}
		}

		return normalized.toString()
	}

	private fun rewrapBlockQuotes(text: String, at: Int): String {
		val normalized = StringBuilder()
		val lines = text.split("\n")
		val quote = StringBuilder()
		for ((index, line) in lines.withIndex()) {
			if (line.startsWith("> ")) {
				quote.append(line.substring(2))
				if (index < lines.size - 1) {
					quote.append(" ")
				}
				continue
			}

			val tailingNewline = index < lines.size - 1
			appendWrappedBlockQuote(quote.toString(), normalized, at - 2, tailingNewline)
			quote.clear()

			normalized.append(line)
			if (tailingNewline) {
				normalized.append("\n")
			}
		}

		appendWrappedBlockQuote(quote.toString(), normalized, at, false)
		return normalized.toString()
	}

	private fun appendWrappedBlockQuote(quote: String, builder: StringBuilder, at: Int, trailingNewline: Boolean): Boolean {
		if (quote.isBlank()) {
			return false
		}

		var start = 0
		var lastWhitespace = -1
		var newLine = false
		for (pos in quote.indices) {
			if (Character.isWhitespace(quote[pos])) {
				lastWhitespace = pos
			}

			if (pos - start < at) {
				continue
			}

			if (newLine) {
				builder.append("\n")
				newLine = false
			}

			if (lastWhitespace >= start) {
				builder.append("> ")
				builder.append(quote.substring(start, lastWhitespace).trim())
				newLine = true
				start = lastWhitespace + 1
				continue
			}
		}

		val remainder = quote.substring(start).trim()
		if (remainder.isNotEmpty()) {
			if (newLine) {
				builder.append("\n")
			}

			builder.append("> ")
			builder.append(remainder)
		}

		if (trailingNewline) {
			builder.append("\n")
		}
		return true
	}

	@Serializable
	data class Config(
		val joinLines: Boolean = true,
		val fixMissingEmptyLineAfterBlockQuote: Boolean = true,
		val changeDoubleToSingleBlockQuotes: Boolean = true,
		val rewrapBlockQuotes: Boolean = true,
		val rewrapBlockQuotesAt: Int = 72
	)
}