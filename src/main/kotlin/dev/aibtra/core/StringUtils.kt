package dev.aibtra.core

import kotlin.math.*

class StringUtils {
	companion object {
		fun determineLineEnding(text: String): Eol? {
			var lastCh: Char? = null
			var targetEol: Eol? = null
			for ((index, ch) in text.withIndex()) {
				val eol: Eol?
				if (lastCh == '\r' && ch == '\n') {
					eol = Eol.WINDOWS
				}
				else if (ch == '\r') {
					if (index < text.length - 1 && text[index + 1] == '\n') {
						lastCh = ch
						continue
					}
					eol = Eol.MACOS
				}
				else if (ch == '\n') {
					eol = Eol.UNIX
				}
				else {
					eol = null
				}

				if (eol != null && eol != targetEol) {
					if (targetEol == null) {
						targetEol = eol
					}
					else {
						return null
					}
				}

				lastCh = ch
			}

			return targetEol ?: Eol.UNIX // If there is no EOL at all, default to UNIX
		}

		fun applyLineEndings(text: String, eol: Eol): String {
			return if (eol != Eol.UNIX) {
				text.replace("\n", eol.sequence)
			}
			else {
				text
			}
		}

		fun fixIndentation(string: String, template: String): String {
			return fixIndentation(string.split("\n"), template.split("\n")).joinToString("\n")
		}

		fun fixIndentation(lines: List<String>, template: List<String>): List<String> {
			val rawLines: List<String> = lines
			val commonExpected = computeCommonIndentation(template)
			val commonActual = computeCommonIndentation(rawLines)
			return rawLines.map {
				if (it.isNotBlank()) {
					commonExpected + it.substring(commonActual.length)
				}
				else {
					it
				}
			}
		}

		private fun computeCommonIndentation(lines: List<String>): String {
			val indentations = lines
				.filter { it.isNotBlank() }
				.map { getIndentation(it) }
			if (indentations.isEmpty()) {
				return ""
			}

			var common = indentations[0]
			for (indent in indentations.drop(1)) {
				val minLength: Int = min(common.length, indent.length)
				var to = 0
				while (to < minLength && common[to] == indent[to]) {
					to++
				}
				common = common.substring(0, to)
				if (common.isEmpty()) {
					break
				}
			}

			return common
		}

		private fun getIndentation(line: String): String {
			for ((i: Int, ch: Char) in line.withIndex()) {
				if (ch != ' ' && ch != '\t') {
					return line.substring(0, i)
				}
			}

			return line
		}
	}

	enum class Eol(val sequence: String) {
		UNIX("\n"), WINDOWS("\r\n"), MACOS("\r")
	}
}
