/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import de.regnis.q.sequence.QSequenceDifference
import de.regnis.q.sequence.QSequenceDifferenceBlock
import de.regnis.q.sequence.QSequenceDifferenceBlockShifter
import de.regnis.q.sequence.core.QSequenceMedia
import de.regnis.q.sequence.media.QSequenceMediaDummyIndexTransformer
import java.util.stream.Collectors

class DiffBuilder(
	private val raw: String,
	private val ref: String,
	private val shift: Boolean,
	private val joinClose: Boolean,
	private val fixCommon: Boolean
) {
	fun build(): List<DiffBlock> {
		val media = SequenceMedia(raw, ref)
		@Suppress("UNCHECKED_CAST") val sequenceBlocks = QSequenceDifference(media, QSequenceMediaDummyIndexTransformer(media)).blocks as List<QSequenceDifferenceBlock>
		QSequenceDifferenceBlockShifter.joinBlocks(sequenceBlocks)

		var blocks = sequenceBlocks.stream().map { block ->
			DiffBlock(block.leftFrom, block.leftTo + 1, block.rightFrom, block.rightTo + 1)
		}.collect(Collectors.toList())

		if (shift) {
			blocks = mergeBlocks(blocks, ::mergeUp)
			blocks = shiftBlocks(blocks)
		}
		if (joinClose) {
			blocks = mergeBlocks(blocks, ::mergeClose)
		}
		if (fixCommon) {
			// This only becomes important when merging blocks, because these blocks won't be "optimal" anymore
			// and hence may allow optimizations.
			blocks = fixCommon(blocks)
		}
		return blocks
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
			if (rawFrom <= rawTo && raw[rawFrom - 1] != raw[rawTo - 1]) {
				break
			}
			if (refFrom <= refTo && ref[refFrom - 1] != ref[refTo - 1]) {
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
		val nextRawFrom = next?.rawFrom ?: raw.length
		val nextRefFrom = next?.refFrom ?: ref.length
		var rawFrom = block.rawFrom
		var rawTo = block.rawTo
		var refFrom = block.refFrom
		var refTo = block.refTo

		while (rawTo < nextRawFrom && refTo < nextRefFrom) {
			if (rawFrom <= rawTo && raw[rawFrom] != raw[rawTo]) {
				break
			}
			if (refFrom <= refTo && ref[refFrom] != ref[refTo]) {
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
			if (wordEndBoundaryCandidate == null && isAtEndOfWord(refTo)) {
				wordEndBoundaryCandidate = DiffBlock(rawFrom, rawTo, refFrom, refTo)
			}
			if (wordStartBoundaryCandidate == null && isAtStartOfWord(refFrom)) {
				wordStartBoundaryCandidate = DiffBlock(rawFrom, rawTo, refFrom, refTo)
			}
			if (rawFrom <= rawTo && raw[rawFrom - 1] != raw[rawTo - 1]) {
				break
			}
			if (refFrom <= refTo && ref[refFrom - 1] != ref[refTo - 1]) {
				break
			}

			rawFrom--
			rawTo--
			refFrom--
			refTo--
		}

		return wordEndBoundaryCandidate ?: (wordStartBoundaryCandidate ?: block)
	}

	private fun isAtEndOfWord(refTo: Int): Boolean {
		if (refTo < 0) {
			return false
		}
		if (refTo == ref.length || refTo < ref.length && ref[refTo].isWhitespace()) {
			return !ref[refTo - 1].isWhitespace()
		}
		return false
	}

	private fun isAtStartOfWord(refFrom: Int): Boolean {
		if (refFrom == ref.length) {
			return false
		}
		if (refFrom == 0 || refFrom > 0 && ref[refFrom - 1].isWhitespace()) {
			return !ref[refFrom].isWhitespace()
		}
		return false
	}

	private fun mergeClose(block: DiffBlock, prev: DiffBlock): DiffBlock? {
		if (prev.refTo + CLOSE_DISTANCE < block.refFrom) {
			return null
		}

		for (pos in block.refFrom until block.refTo) {
			if (ref[pos].isWhitespace()) {
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
				&& raw[rawFrom] == ref[refFrom]) {
				rawFrom++
				refFrom++
			}

			while (rawTo > rawFrom
				&& refTo > refFrom
				&& raw[rawTo - 1] == ref[refTo - 1]) {
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

	private class SequenceMedia(val raw: String, val ref: String) : QSequenceMedia {
		override fun equals(leftIndex: Int, rightIndex: Int): Boolean = raw[leftIndex] == ref[rightIndex]
		override fun getLeftLength(): Int = raw.length
		override fun getRightLength(): Int = ref.length
	}
}