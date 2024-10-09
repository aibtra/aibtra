package dev.aibtra.diff

data class Diff(val raw: String, val ref: String, val blocks: List<DiffBlock>, val orgDiff: OrgDiff, val refFinished: Boolean) {
	companion object {
		val INITIAL: Diff = Diff("", "", listOf(), OrgDiff("", "", "", 0, 0, listOf()), false)
	}

	data class OrgDiff(val raw: String, val ref: String, val refExtended: String, val rawPredictionStart: Int, val refPredictionStart: Int, val blocks: List<DiffBlock>) {
		init {
			require(ref.length == refPredictionStart) // refPredictionStart is only their for symmetry and code clarity
		}
	}
}