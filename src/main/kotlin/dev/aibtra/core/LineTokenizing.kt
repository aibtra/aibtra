package dev.aibtra.core

class LineTokenizing private constructor(private val lines: List<IntRange>, private val tokenizing: AlphaNumericTokenizing) {

	private val endsWithEol : Boolean

	init {
		val lastLine = lines[lines.size - 1]
		endsWithEol = lastLine.first <= lastLine.last && tokenizing[lastLine.last] == "\n"
	}

	val size: Int
		get() = lines.size

	override fun toString(): String {
		return lines.mapIndexed { index, range ->
			"$range: " + string(index, false, true)
		}.joinToString("")
	}

	fun indices(): IntRange {
		return lines.indices
	}

	fun isNotEmpty(): Boolean {
		return lines.isNotEmpty()
	}

	fun subTokenizing(range: IntRange): LineTokenizing {
		return LineTokenizing(lines.subList(range.first, range.last + 1), tokenizing)
	}

	fun firstIndexOf(other: LineTokenizing, canonical: Boolean): Int? {
		for (i in 0..lines.size - other.size) {
			if (equals(other, i until i + other.size, other.lines.indices, canonical)) {
				return i
			}
		}

		return null
	}

	fun lastIndexOf(other: LineTokenizing, canonical: Boolean): Int? {
		for (i in lines.size - other.size downTo 0) {
			if (equals(other, i until i + other.size, other.lines.indices, canonical)) {
				return i
			}
		}

		return null
	}

	fun equals(other: LineTokenizing, range: IntRange, otherRange: IntRange, canonical: Boolean): Boolean {
		val includeEol = !canonical
		val tokens = range(range.first, includeEol).first..range(range.last, includeEol).last
		val otherTokens = other.range(otherRange.first, includeEol).first..other.range(otherRange.last, includeEol).last
		return tokenizing.equals(other.tokenizing, tokens, otherTokens, canonical)
	}

	fun tokens(line: Int, canonical: Boolean, includeEol: Boolean): List<String> {
		return tokenizing.tokens(canonical, range(line, includeEol))
	}

	fun string(line: Int, canonical: Boolean, includeEol: Boolean): String {
		return tokens(line, canonical, includeEol).joinToString("")
	}

	fun string(): String {
		return List(lines.size) { index -> string(index, false, true) }.joinToString("")
	}

	private fun range(line: Int, includeEol: Boolean): IntRange {
		val range = lines[line]
		return if (line == lines.size - 1 && !endsWithEol
			|| includeEol) {
			range.first..range.last
		}
		else {
			range.first until range.last
		}
	}

	companion object {
		fun tokenize(input: String): LineTokenizing {
			val tokenizing = AlphaNumericTokenizing.tokenize(input)
			val lines = mutableListOf<IntRange>()
			var start = 0
			for ((index, token) in tokenizing.withIndex()) {
				if (token == "\n") {
					val end = index + 1
					lines.add(start until end)
					start = end
				}
			}

			lines.add(start until tokenizing.length)
			return LineTokenizing(lines, tokenizing)
		}
	}
}
