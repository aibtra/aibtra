package dev.aibtra.core

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
	}

	enum class Eol(val sequence: String) {
		UNIX("\n"), WINDOWS("\r\n"), MACOS("\r")
	}
}
