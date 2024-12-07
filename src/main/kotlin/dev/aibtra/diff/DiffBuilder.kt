/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import de.regnis.q.sequence.*
import de.regnis.q.sequence.media.*
import java.util.stream.*

class DiffBuilder(
	rawString: String,
	refString: String,
	private val shift: Boolean,
	private val joinClose: Boolean,
	private val fixCommon: Boolean,
	tokenizingMode: DiffTokenizingMode
) {
	private val tokenizing = DiffTokenizing.create(rawString, refString, tokenizingMode)

	fun build(): List<DiffBlock> {
		val media = tokenizing.createMedia()
		@Suppress("UNCHECKED_CAST") val sequenceBlocks = QSequenceDifference(media, QSequenceMediaDummyIndexTransformer(media)).blocks as List<QSequenceDifferenceBlock>
		QSequenceDifferenceBlockShifter.joinBlocks(sequenceBlocks)

		var tokenBlocks = sequenceBlocks.stream().map { block ->
			DiffBlock(block.leftFrom, block.leftTo + 1, block.rightFrom, block.rightTo + 1)
		}.collect(Collectors.toList())

		if (shift) {
			tokenBlocks = mergeBlocks(tokenBlocks, ::mergeUp)
			tokenBlocks = shiftBlocks(tokenBlocks)
		}
		if (joinClose) {
			tokenBlocks = mergeBlocks(tokenBlocks, ::mergeClose)
		}
		if (fixCommon) {
			// This only becomes important when joining close blocks, because these blocks won't be "optimal" anymore
			// and hence may allow optimizations.
			tokenBlocks = fixCommon(tokenBlocks)
		}

		return tokenBlocks.map { tokenizing.toCharBlock(it) }
	}

	private fun shiftBlocks(orgBlocks: List<DiffBlock>): List<DiffBlock> {
		val blocks: MutableList<DiffBlock> = mutableListOf()
		for ((index, orgBlock) in orgBlocks.withIndex()) {
			val nextBlock = if (index < orgBlocks.size - 1) orgBlocks[index + 1] else null
			val prevBlock = if (index > 0) orgBlocks[index - 1] else null
			val shiftedDown = shiftDownAsMuchAsPossible(orgBlock, nextBlock)
			val shiftedUp = shiftUpToBestWordBoundary(shiftedDown, prevBlock)
			blocks.add(shiftedUp)
		}
		return blocks
	}

	private fun mergeUp(block: DiffBlock, prev: DiffBlock): DiffBlock? {
		val prevRawTo = prev.rawTo
		val prevRefTo = prev.refTo
		var rawFrom = block.rawFrom
		var rawTo = block.rawTo
		var refFrom = block.refFrom
		var refTo = block.refTo

		while (rawFrom > prevRawTo && refFrom > prevRefTo) {
			if (rawFrom <= rawTo && !tokenizing.equalsRaw(rawFrom - 1, rawTo - 1)) {
				break
			}
			if (refFrom <= refTo && !tokenizing.equalsRef(refFrom - 1, refTo - 1)) {
				break
			}

			rawFrom--
			rawTo--
			refFrom--
			refTo--
		}

		if (rawFrom != prevRawTo || refFrom != prevRefTo) {
			return null
		}

		return DiffBlock(prev.rawFrom, rawTo, prev.refFrom, refTo)
	}

	private fun shiftDownAsMuchAsPossible(block: DiffBlock, next: DiffBlock?): DiffBlock {
		val nextRawFrom = next?.rawFrom ?: tokenizing.getRawLength()
		val nextRefFrom = next?.refFrom ?: tokenizing.getRefLength()
		var rawFrom = block.rawFrom
		var rawTo = block.rawTo
		var refFrom = block.refFrom
		var refTo = block.refTo

		while (rawTo < nextRawFrom && refTo < nextRefFrom) {
			if (rawFrom <= rawTo && !tokenizing.equalsRaw(rawFrom, rawTo)) {
				break
			}
			if (refFrom <= refTo && !tokenizing.equalsRef(refFrom, refTo)) {
				break
			}

			rawFrom++
			rawTo++
			refFrom++
			refTo++
		}

		return DiffBlock(rawFrom, rawTo, refFrom, refTo)
	}

	private fun shiftUpToBestWordBoundary(block: DiffBlock, prev: DiffBlock?): DiffBlock {
		val prevRawTo = prev?.rawTo ?: 0
		val prevRefTo = prev?.refTo ?: 0
		var rawFrom = block.rawFrom
		var rawTo = block.rawTo
		var refFrom = block.refFrom
		var refTo = block.refTo

		var wordEndBoundaryCandidate: DiffBlock? = null
		var wordStartBoundaryCandidate: DiffBlock? = null

		while (rawFrom > prevRawTo && refFrom > prevRefTo) {
			if (wordEndBoundaryCandidate == null && tokenizing.isRefAtEndOfWord(refTo)) {
				wordEndBoundaryCandidate = DiffBlock(rawFrom, rawTo, refFrom, refTo)
			}
			if (wordStartBoundaryCandidate == null && tokenizing.isRefAtStartOfWord(refFrom)) {
				wordStartBoundaryCandidate = DiffBlock(rawFrom, rawTo, refFrom, refTo)
			}
			if (rawFrom <= rawTo && !tokenizing.equalsRaw(rawFrom - 1, rawTo - 1)) {
				break
			}
			if (refFrom <= refTo && !tokenizing.equalsRef(refFrom - 1, refTo - 1)) {
				break
			}

			rawFrom--
			rawTo--
			refFrom--
			refTo--
		}

		return wordEndBoundaryCandidate ?: (wordStartBoundaryCandidate ?: block)
	}

	private fun mergeClose(block: DiffBlock, prev: DiffBlock): DiffBlock? {
		if (prev.refTo + CLOSE_DISTANCE < block.refFrom) {
			return null
		}

		for (pos in block.refFrom until block.refTo) {
			if (tokenizing.isRefWhitespace(pos)) {
				return null
			}
		}

		return DiffBlock(prev.rawFrom, block.rawTo, prev.refFrom, block.refTo)
	}

	private fun mergeBlocks(orgBlocks: List<DiffBlock>, operation: (block: DiffBlock, prev: DiffBlock) -> DiffBlock?): MutableList<DiffBlock> {
		val blocks = mutableListOf<DiffBlock>()
		for ((index, orgBlock) in orgBlocks.withIndex()) {
			if (index == 0) {
				blocks.add(orgBlock)
				continue
			}

			val prevBlock = blocks.last()
			val mergedBlock = operation(orgBlock, prevBlock)
			if (mergedBlock == null) {
				blocks.add(orgBlock)
			}
			else {
				blocks.removeLast()
				blocks.add(mergedBlock)
			}
		}

		return blocks
	}

	private fun fixCommon(orgBlocks: List<DiffBlock>): MutableList<DiffBlock> {
		val blocks = mutableListOf<DiffBlock>()
		for (orgBlock in orgBlocks) {
			var rawFrom = orgBlock.rawFrom
			var rawTo = orgBlock.rawTo
			var refFrom = orgBlock.refFrom
			var refTo = orgBlock.refTo

			while (rawFrom < rawTo
				&& refFrom < refTo
				&& tokenizing.equals(rawFrom, refFrom)) {
				rawFrom++
				refFrom++
			}

			while (rawTo > rawFrom
				&& refTo > refFrom
				&& tokenizing.equals(rawTo - 1, refTo - 1)) {
				rawTo--
				refTo--
			}

			val block = if (
				rawFrom == orgBlock.rawFrom
				&& refFrom == orgBlock.refFrom
				&& rawTo == orgBlock.rawTo
				&& refTo == orgBlock.refTo
			) {
				orgBlock
			}
			else {
				DiffBlock(rawFrom, rawTo, refFrom, refTo)
			}

			blocks.add(block)
		}

		return blocks
	}

	companion object {
		private const val CLOSE_DISTANCE = 3
	}
}