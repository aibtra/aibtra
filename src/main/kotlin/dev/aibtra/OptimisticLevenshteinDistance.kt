package dev.aibtra

import kotlin.math.min

class OptimisticLevenshteinDistance private constructor(
	private val rowString: String,
	private val colString: String,
	private val limit: (row: Int, col: Int, editDistance: Int) -> Boolean
) {

	private val rowCount = rowString.length
	private val colCount = colString.length
	private val matrix = mutableMapOf<Location, Int>()
	private var distance = -1
	private var steps : Long = 0

	fun distance() = distance

	fun steps() = steps

	private fun run() {
		if (rowCount == 0) {
			distance = colCount
			return
		}
		if (colCount == 0) {
			distance = rowCount
			return
		}

		val origin = Location(0, 0)
		val target = Location(rowCount, colCount)
		var front = mutableListOf(Entry(origin, 0))
		var limitReached = false
		while (!limitReached) {
			matrix[target]?.let {
				distance = it
				return
			}

			require(front.isNotEmpty())

			val forwardFront = mutableListOf<Entry>()
			for (entry in front) {
				forward(entry, forwardFront)
			}

			front = forwardFront

			matrix[target]?.let {
				distance = it
				return
			}

			val stepFront = mutableListOf<Entry>()
			for (entry in front) {
				limitReached = limitReached or step(entry, 1, 0, stepFront)
				limitReached = limitReached or step(entry, 0, 1, stepFront)
				limitReached = limitReached or step(entry, 1, 1, stepFront)
			}

			front = stepFront
		}

		require(limitReached)
	}

	private fun forward(entry : Entry, front: MutableList<Entry>) {
		val rowStart = entry.location.row
		val colStart = entry.location.col
		val dis = entry.editDistance

		var lastLocation = entry.location
		for (diag in 1 .. min(rowCount - rowStart, colCount - colStart)) {
			val row = rowStart + diag
			val col = colStart + diag
			val location = Location(row, col)
			if (matrix[location] != null) {
				return
			}

			if (rowString[row - 1] != colString[col - 1]) {
				front.add(Entry(lastLocation, dis))
				return
			}

			matrix[location] = dis
			steps++
			lastLocation = location
		}

		front.add(Entry(lastLocation, dis))
	}

	private fun step(entry : Entry, rowInc : Int, colInc: Int, nextSchedule: MutableList<Entry>) : Boolean {
		val row = entry.location.row
		val col = entry.location.col
		val location = Location(row + rowInc, col + colInc)
		if (matrix[location] != null) {
			return false
		}

		val dis = entry.editDistance + 1
		if (limit(location.row, location.col, dis)) {
			return true
		}

		matrix[location] = dis
		steps++
		nextSchedule.add(Entry(location, dis))
		return false
	}

	@Suppress("NAME_SHADOWING", "unused")
	private fun printMatrix() {
		val builder = StringBuilder()
		builder.append("    ")
		for (col in 0 until colCount) {
			builder.append(colString[col])
			builder.append(" ")
		}
		println(builder)

		for (row in 0 .. rowCount) {
			val builder = StringBuilder()
			if (row >= 1) {
				builder.append(rowString[row - 1])
			}
			else {
				builder.append(" ")
			}
			builder.append(" ")

			for (col in 0 .. colCount) {
				builder.append(matrix[Location(row, col)]?.let { '0' + it } ?: '.')
				builder.append(" ")
			}
			println(builder.toString())
		}
	}

	companion object {
		fun compute(rowString: String, colString: String, limit: (row: Int, col: Int, editDistance: Int) -> Boolean): OptimisticLevenshteinDistance {
			val distance = OptimisticLevenshteinDistance(rowString, colString, limit)
			distance.run()
			return distance
		}
	}

	private data class Location(val row: Int, val col: Int)

	private data class Entry(val location: Location, val editDistance: Int)
}