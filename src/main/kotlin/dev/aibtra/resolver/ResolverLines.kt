package dev.aibtra.resolver

internal class ResolverLines(text: String) {
	val strings: List<String>
	val size: Int
		get() = strings.size

	private val indexes: IntArray
	private val offsets: IntArray

	init {
		val indexes = IntArray(text.length)
		val offsets = mutableListOf<Int>()
		val strings = mutableListOf<String>()
		var start = 0
		var index = 0
		val length = text.length

		offsets.add(0)
		while (index < length) {
			val ch = text[index]
			require(ch != '\r')

			indexes[index] = strings.size

			if (ch == '\n') {
				strings.add(text.substring(start, index))
				start = index + 1
				offsets.add(start)
			}
			else if (index == length - 1) {
				strings.add(text.substring(start, index + 1))
			}

			index++
		}

		if (start >= length) {
			strings.add("")
		}
		offsets.add(length) // for convenience, to allow accessing exclusive to-indexes

		this.strings = strings
		this.indexes = indexes
		this.offsets = offsets.toIntArray()
	}

	operator fun get(i: Int): String {
		return strings[i]
	}

	fun index(offset: Int): Int {
		return indexes[offset]
	}

	fun offset(index: Int): Int {
		return offsets[index]
	}

	fun subString(lineFrom: Int, lineTo: Int = strings.size): String {
		val list = strings.subList(lineFrom, lineTo)
		if (list.isEmpty()) {
			return ""
		}

		val string = list.joinToString("\n")
		if (lineTo < strings.size) {
			return string + "\n"
		}
		return string
	}

	fun subList(lineFrom: Int, lineTo: Int = strings.size): List<String> {
		return strings.subList(lineFrom, lineTo)
	}
}