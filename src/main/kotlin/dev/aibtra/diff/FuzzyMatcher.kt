package dev.aibtra.diff

import dev.aibtra.OptimisticLevenshteinDistance

class FuzzyMatcher(private val errorThreshold: Int, private val errorRatio: Int) {

	fun findBestMatch(haystackString: String, needleString: String, start: Int): Int {
		require(!haystackString.contains("\r"))
		require(!needleString.contains("\r"))

		val haystackIndexes = getLineStartIndexes(haystackString)
		val needleIndexes = getLineStartIndexes(needleString)
		val startLine = haystackIndexes.binarySearch(start).let { if (it < 0) -(it + 1) else it }
		val count = needleIndexes.size
		var bestStart = -1
		var bestDistance = Int.MAX_VALUE
		for (line in startLine..haystackIndexes.size - count) {
			val from = haystackIndexes[line]
			val to = if (line + count < haystackIndexes.size) haystackIndexes[line + count] - 1 else haystackString.length
			val haystack = haystackString.substring(from, to)
			val distance = OptimisticLevenshteinDistance.compute(haystack, needleString) { row, col, distance ->
				if (row >= 100 && distance > errorThreshold && distance * errorRatio > row + col) {
					println()
				}
				distance > errorThreshold && distance * errorRatio > row + col
			}.distance()
			if (distance >= 0 &&
				bestDistance > distance &&
				(distance <= errorThreshold || distance * errorRatio.toLong() <= needleString.length)) {
				bestStart = from
				bestDistance = distance
			}
		}

		return bestStart
	}

	private fun getLineStartIndexes(input: String): IntArray {
		val indexes = mutableListOf<Int>()
		indexes.add(0)
		for (i in input.indices) {
			if (input[i] == '\n') {
				indexes.add(i + 1)
			}
		}
		return indexes.toIntArray()
	}
}

