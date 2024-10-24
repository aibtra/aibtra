package dev.aibtra.diff

class DiffExtender(private val joinCloseBlocks: Boolean = true) {
	fun extend(raw: String, ref: String, diff: Diff, finished: Boolean): Diff {
		// If ref is empty, this is a new request, hence reset.
		if (ref.isEmpty()) {
			return Diff(raw, ref, listOf(), Diff.OrgDiff(raw, "", "", 0, 0, listOf()), false)
		}

		// If the ref is already finished, we do a normal diff
		if (finished ||
			diff.refFinished) { // A shrinking ref is unexpected, we don't know what happened, hence treat as finished
			val blocks = createBlocks(raw, ref)
			return Diff(raw, ref, blocks, Diff.OrgDiff(raw, ref, "", raw.length, ref.length, blocks), true)
		}

		val orgDiff = extendOrgDiff(ref, diff.orgDiff)
		val blocksExtended = createBlocks(raw, orgDiff.refExtended)
		val blocks = blocksExtended.filter { it.refTo < ref.length } // "< ref.length" because we don't want to see the big final removed block during build
		return Diff(raw, ref, blocks, orgDiff, false)
	}

	private fun createBlocks(raw: String, ref: String): List<DiffBlock> {
		if (ref.isEmpty()) {
			return listOf()
		}

		return DiffBuilder(raw, ref, true, joinCloseBlocks, joinCloseBlocks).build()
	}

	private fun extendOrgDiff(ref: String, diff: Diff.OrgDiff): Diff.OrgDiff {
		require(diff.raw.isNotEmpty()) // ensure raw has been initialized; Submit must not be enabled if raw is empty

		val rawTo: Int
		val refTo: Int
		if (diff.blocks.isEmpty()) {
			rawTo = 0
			refTo = 0
		}
		else {
			val refCommon = ref.commonPrefixWith(diff.ref)
			val lastBlock = diff.blocks.findLast { it.refTo < refCommon.length }
			rawTo = lastBlock?.rawTo ?: 0
			refTo = lastBlock?.refTo ?: 0
		}

		val refPredictionStart = ref.length
		val refDiff = refPredictionStart - refTo
		require(refDiff >= 0)

		val rawPredictionStart = Math.min(diff.raw.length, Math.max(rawTo + refDiff, diff.rawPredictionStart))
		val refPrediction = diff.raw.substring(rawPredictionStart)
		val refExtended = ref + refPrediction
		val blocksExtended = createBlocks(diff.raw, refExtended)
		val blocks = blocksExtended.filter { it.refTo < ref.length } // "< ref.length" because we don't want to see the big final removed block during build
		return Diff.OrgDiff(diff.raw, ref, refExtended, rawPredictionStart, refPredictionStart, blocks)
	}
}