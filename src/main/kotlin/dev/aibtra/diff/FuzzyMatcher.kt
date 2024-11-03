package dev.aibtra.diff

import dev.aibtra.OptimisticLevenshteinDistance

class FuzzyMatcher(val from: Int, val to: Int, val steps: Long) {
	init {
		require(
			from < to ||
							from == 0 && to == 0 ||
							from == -1 && to == -1
		)
	}

	companion object {
		fun findBestMatch(haystackStringRaw: String, needleStringRaw: String, start: Int, errorThreshold: Int, errorRatio: Int): FuzzyMatcher {
			require(!haystackStringRaw.contains("\r"))
			require(!needleStringRaw.contains("\r"))

			val haystackIndexesRaw = getLineStartIndexes(haystackStringRaw)
			val startLine = haystackIndexesRaw.binarySearch(start).let { if (it < 0) -(it + 1) else it }

			val haystackString = trimLines(haystackStringRaw)
			val needleString = trimLines(needleStringRaw)
			val haystackIndexes = getLineStartIndexes(haystackString)
			val needleIndexes = getLineStartIndexes(needleString)
			val count = needleIndexes.size
			var bestStartLine = -1
			var bestDistance = Int.MAX_VALUE
			var steps: Long = 0

			val mainRange = startLine .. haystackIndexes.size - count
			val preRange = 0 until startLine
			for (range in listOf(mainRange, preRange)) {
				for (line in range) {
					val from = haystackIndexes[line]
					val to = if (line + count < haystackIndexes.size) haystackIndexes[line + count] - 1 else haystackString.length
					val haystack = haystackString.substring(from, to)
					val distance = OptimisticLevenshteinDistance.compute(haystack, needleString) { row, col, distance ->
						distance > errorThreshold && distance * errorRatio > row + col
					}
					val d = distance.distance()
					if (d >= 0 &&
						bestDistance > d &&
						(d <= errorThreshold || d * errorRatio.toLong() <= needleString.length)) {
						bestStartLine = line
						bestDistance = d
					}
					steps += distance.steps()
				}
			}

			if (bestStartLine >= 0) {
				val bestFrom = haystackIndexesRaw[bestStartLine]
				val bestEndLine = bestStartLine + count
				val bestTo = if (bestEndLine < haystackIndexesRaw.size) haystackIndexesRaw[bestEndLine] - 1 else haystackStringRaw.length
				return FuzzyMatcher(bestFrom, bestTo, steps)
			}
			else {
				return FuzzyMatcher(-1, -1, steps)
			}
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

		private fun trimLines(input: String): String {
			return input.lines().map { it.trim() }.joinToString("\n")
		}
	}
}

