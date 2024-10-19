package dev.aibtra.diff

import java.io.IOException

class UnifiedDiff private constructor(private val hunks: List<Hunk>) {

	fun applyHunks(base: String): String {
		val baseLines = base.lines()
		val outputLines = mutableListOf<String>()
		var baseLineIndex = 0
		for (hunk in hunks) {
			val oldStartIndex = hunk.oldStart - 1
			while (baseLineIndex < oldStartIndex) {
				outputLines.add(baseLines[baseLineIndex])
				baseLineIndex++
			}

			for (hunkLine in hunk.lines) {
				when {
					hunkLine.startsWith(" ") || hunkLine.startsWith("  ") -> {
						val line = hunkLine.substring(1)
						if (baseLines[baseLineIndex] != line) {
							throw IOException("Mismatch for line '$line'")
						}
						outputLines.add(line)
						baseLineIndex++
					}

					hunkLine.startsWith("-") -> {
						val line = hunkLine.substring(1)
						if (baseLines[baseLineIndex] != line) {
							throw IOException("Mismatch for line '$line'")
						}
						baseLineIndex++
					}

					hunkLine.startsWith("+") -> {
						val line = hunkLine.substring(1)
						outputLines.add(line)
					}

					else -> {
						throw IOException("Invalid hunk line: $hunkLine")
					}
				}
			}
		}

		while (baseLineIndex < baseLines.size) {
			outputLines.add(baseLines[baseLineIndex])
			baseLineIndex++
		}

		return outputLines.joinToString(separator = "\n")
	}

	companion object {
		fun parse(diffString: String): UnifiedDiff {
			val hunks = mutableListOf<Hunk>()
			val lines = diffString.lines()
			var index = 0

			while (index < lines.size) {
				val line = lines[index]
				if (line.startsWith("@@")) {
					val hunkHeader = line
					val hunkLines = mutableListOf<String>()
					index++

					val regex = """@@ -(\d+),?(\d*) \+(\d+),?(\d*) @@""".toRegex()
					val matchResult = regex.find(hunkHeader)
					if (matchResult != null) {
						val (oldStartStr, oldLinesStr, newStartStr, newLinesStr) = matchResult.destructured

						val oldStart = oldStartStr.toInt()
						val oldLinesCount = if (oldLinesStr.isEmpty()) 1 else oldLinesStr.toInt()
						val newStart = newStartStr.toInt()
						val newLinesCount = if (newLinesStr.isEmpty()) 1 else newLinesStr.toInt()

						while (index < lines.size && !lines[index].startsWith("@@")) {
							hunkLines.add(lines[index])
							index++
						}

						val hunk = Hunk(
							oldStart = oldStart,
							oldLines = oldLinesCount,
							newStart = newStart,
							newLines = newLinesCount,
							lines = hunkLines
						)
						hunks.add(hunk)
					}
					else {
						println("Invalid hunk header: $hunkHeader")
						index++
					}
				}
				else {
					index++
				}
			}

			return UnifiedDiff(hunks)
		}
	}

	private data class Hunk(
		val oldStart: Int,
		val oldLines: Int,
		val newStart: Int,
		val newLines: Int,
		val lines: List<String>
	)
}