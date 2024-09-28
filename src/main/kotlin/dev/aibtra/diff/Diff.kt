package dev.aibtra.diff

data class Diff(val raw: String, val rawTo: Int, val ref: String, val blocks: List<DiffBlock>, val refFinished: Boolean) {
	companion object {
		val INITIAL: Diff = Diff("", 0, "", listOf(), false)
	}
}