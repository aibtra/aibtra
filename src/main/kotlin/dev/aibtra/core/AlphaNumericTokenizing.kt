package dev.aibtra.core

class AlphaNumericTokenizing private constructor(
	private val tokens: List<String>,
	private val canonical: List<String?>,
	private val positions: List<Int>
) : Iterable<String> {

	val length: Int
		get() = tokens.size

	fun equals(other: AlphaNumericTokenizing, range: IntRange, otherRange: IntRange, canonical: Boolean): Boolean {
		return subList(this, range, canonical) == subList(other, otherRange, canonical)
	}

	override fun iterator(): Iterator<String> {
		return tokens.iterator()
	}

	operator fun get(index: Int): String {
		return tokens[index]
	}

	fun tokens(canonical: Boolean, range: IntRange? = null): List<String> {
		return subList(this, range ?: tokens.indices, canonical)
	}

	fun charPos(index: Int): Int {
		return positions[index]
	}

	companion object {
		const val CANONICAL_WHITESPACE = " "

		fun tokenize(input: String): AlphaNumericTokenizing {
			val tokens = mutableListOf<String>()
			val canonical = mutableListOf<String?>()
			val positions = mutableListOf<Int>()

			var index = 0
			var lineStart = true
			val length = input.length

			while (index < length) {
				val currentChar = input[index]
				require(currentChar != '\r')

				when {
					isWordChar(currentChar) -> {
						val start = index
						while (index < length && isWordChar(input[index])) {
							index++
						}
						val token = input.substring(start, index)
						tokens.add(token)
						canonical.add(token)
						positions.add(start)
					}

					isWhitespaceChar(currentChar) -> {
						val start = index
						while (index < length && isWhitespaceChar(input[index])) {
							index++
						}
						val token = input.substring(start, index)
						tokens.add(token)
						val lineEnd = index == length || input[index] == '\n'
						if (!lineStart && !lineEnd) {
							canonical.add(CANONICAL_WHITESPACE)
						}
						else {
							canonical.add(null)
						}
						positions.add(start)
					}

					else -> {
						val string = currentChar.toString()
						tokens.add(string)
						canonical.add(string)
						positions.add(index)
						index++
					}
				}

				lineStart = currentChar == '\n'
			}

			positions.add(length)
			return AlphaNumericTokenizing(tokens, canonical, positions)
		}

		private fun isWordChar(c: Char): Boolean {
			return c.isLetterOrDigit() || c == '_'
		}

		private fun isWhitespaceChar(c: Char): Boolean {
			return c == ' ' || c == '\t'
		}

		private fun subList(tokenizing: AlphaNumericTokenizing, range: IntRange, canonical: Boolean): List<String> {
			if (canonical) {
				return tokenizing.canonical.subList(range.first, range.last + 1).filterNotNull()
			}

			return tokenizing.tokens.subList(range.first, range.last + 1)
		}
	}
}
