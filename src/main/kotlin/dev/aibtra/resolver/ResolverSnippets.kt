package dev.aibtra.resolver

import de.regnis.q.sequence.*
import de.regnis.q.sequence.core.*
import de.regnis.q.sequence.media.*
import dev.aibtra.core.*
import dev.aibtra.core.SequenceBlock.*
import java.math.*
import java.security.*
import kotlin.math.*

data class ResolverSnippets(val files: ResolverFiles, private val list: List<ResolverSnippet>) : Iterable<ResolverSnippet> {
	init {
		require(list.map { it.id }.areAllUnique())
	}

	override fun iterator(): Iterator<ResolverSnippet> {
		return list.iterator()
	}

	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}

	fun size(): Int {
		return list.size
	}

	companion object {
		fun compute(files: ResolverFiles, context: Int): ResolverSnippets {
			val ids = Ids()
			val snippets = mutableListOf<ResolverSnippet>()
			for (file in files.files) {
				snippets.addAll(compute(file, ids, context))
			}
			return ResolverSnippets(files, snippets)
		}

		private fun compute(file: ResolverFile, ids: Ids, context: Int): List<ResolverSnippet> {
			// All ranges are inclusive.
			val draftContent = file.draftContent
			val baseLines = ResolverLines(file.baseContent)
			val oursLines = ResolverLines(file.oursContent)
			val theirsLines = ResolverLines(file.theirsContent)
			val draftLines = ResolverLines(draftContent)
			val allConflicts = ResolverConflict.extract(draftContent)
			val reducedContent = reduceContent(draftContent, allConflicts)
			val reducedLines = ResolverLines(reducedContent)
			val conflictToOursRange = findConflicts(oursLines, reducedLines, allConflicts, Version.OURS)
			val conflictToTheirsRange = findConflicts(theirsLines, reducedLines, allConflicts, Version.THEIRS)
			val validConflicts = allConflicts.intersect(conflictToOursRange.keys.plus(conflictToTheirsRange.keys))
			val baseOursDiff = createDiff(baseLines, oursLines)
			val baseTheirsDiff = createDiff(baseLines, theirsLines)
			val oursToBaseFrom = createMapping(baseOursDiff, Side.RIGHT, Mode.FROM)
			val oursToBaseTo = createMapping(baseOursDiff, Side.RIGHT, Mode.TO)
			val theirsToBaseFrom = createMapping(baseTheirsDiff, Side.RIGHT, Mode.FROM)
			val theirsToBaseTo = createMapping(baseTheirsDiff, Side.RIGHT, Mode.TO)
			val baseToOursFrom = createMapping(baseOursDiff, Side.LEFT, Mode.FROM)
			val baseToOursTo = createMapping(baseOursDiff, Side.LEFT, Mode.TO)
			val baseToTheirsFrom = createMapping(baseTheirsDiff, Side.LEFT, Mode.FROM)
			val baseToTheirsTo = createMapping(baseTheirsDiff, Side.LEFT, Mode.TO)
			return validConflicts.map { conflict ->
				val (oursRawStart, oursBaseFrom, oursBaseTo) = conflictToOursRange[conflict]?.let { range ->
					Triple(range.first, oursToBaseFrom[range.first], oursToBaseTo[range.last])
				} ?: Triple(0, Int.MAX_VALUE, Int.MIN_VALUE)

				val (theirsRawStart, theirsBaseFrom, theirsBaseTo) = conflictToTheirsRange[conflict]?.let { range ->
					Triple(range.first, theirsToBaseFrom[range.first], theirsToBaseTo[range.last])
				} ?: Triple(0, Int.MAX_VALUE, Int.MIN_VALUE)

				val baseFrom = min(oursBaseFrom, theirsBaseFrom)
				val baseTo = max(oursBaseTo, theirsBaseTo)
				val oursFrom = baseToOursFrom[baseFrom]
				val oursTo = baseToOursTo[baseTo]
				val theirsFrom = baseToTheirsFrom[baseFrom]
				val theirsTo = baseToTheirsTo[baseTo]
				val conflictStart = draftLines.index(conflict.range.first)
				val conflictEnd = draftLines.index(conflict.range.last)
				val draftResult = createContent(draftLines, conflictStart..conflictEnd, context)
				val draftPart = draftResult.first
				val draftPortion = ResolverDraftPortion(file, draftResult.second, draftResult.third)
				val oursPart = createContent(oursLines, IntRange(oursFrom, oursTo), context).first
				val theirsPart = createContent(theirsLines, IntRange(theirsFrom, theirsTo), context).first
				val basePart = createContent(baseLines, IntRange(baseFrom, baseTo), context).first
				val id = ids.createId(basePart.join(false), oursPart.join(false), oursRawStart, theirsPart.join(false), theirsRawStart)
				val warning = checkForProblems(draftPortion, basePart, oursPart, theirsPart, context)
				ResolverSnippet(id, file, conflict, draftPart, basePart, oursPart, theirsPart, draftPortion, warning)
			}
		}

		private fun checkForProblems(draftPortion: ResolverDraftPortion, basePart: ResolverSnippet.Content, oursPart: ResolverSnippet.Content, theirsPart: ResolverSnippet.Content, context: Int): String? {
			val draftLineCount = draftPortion.file.draftContent.substring(draftPortion.start, draftPortion.end).split("\n").size
			val base = Pair("Base", basePart.join(false).split("\n").size)
			val ours = Pair("Ours", oursPart.join(false).split("\n").size)
			val theirs = Pair("Theirs", theirsPart.join(false).split("\n").size)
			val (maxName, maxLineCount) = listOf(base, ours, theirs).maxBy { it.second }
			if (maxLineCount > draftLineCount + 2 * context) {
				return "'$maxName' snippet contains $maxLineCount; confirm to send!"
			}
			return null
		}

		private fun createMapping(diff: Diff, src: Side, mode: Mode): IntArray {
			val dst = src.other()
			val srcLength = diff.length(src)
			val mapping = IntArray(srcLength) { -1 }

			var lastSrcPos = 0
			var lastMappedPos = 0
			for (block in diff.blocks) {
				val srcFrom = block[src, Mode.FROM]
				val srcTo = block[src, Mode.TO] - 1
				for (i in lastSrcPos until srcFrom) {
					mapping[i] = lastMappedPos++
				}

				val mapped = max(block[dst, Mode.FROM], block[dst, mode] - (if (mode == Mode.FROM) 0 else 1))
				for (i in srcFrom..srcTo) {
					mapping[i] = mapped
				}

				lastSrcPos = srcTo + 1
				lastMappedPos = max(mapped, block[dst, Mode.TO] - 1)
			}
			for (i in lastSrcPos until srcLength) {
				mapping[i] = lastMappedPos++
			}

			val dstLength = diff.length(dst)
			require(mapping.all { it in 0..dstLength })
			mapping.asList().zipWithNext { a, b -> require(a <= b) }
			return mapping
		}

		private fun findConflicts(otherLines: ResolverLines, reducedLines: ResolverLines, conflicts: List<ResolverConflict>, version: Version): Map<ResolverConflict, IntRange> {
			val blocks = createBlocks(otherLines, reducedLines)
			if (blocks.isEmpty()) {
				require(conflicts.stream().allMatch { it[version].isEmpty() })
				return mapOf()
			}

			var startIndex = 0
			var blockIndex = 0
			return buildMap {
				// A conflict may not be contained in a single block, but only intersect blocks
				for (conflict in conflicts) {
					findConflict(otherLines, startIndex, conflict, version) { range ->
						while (blockIndex < blocks.size) {
							val block = blocks[blockIndex]
							if (range.first <= block.leftTo - 1 && range.last >= block.leftFrom) {
								put(conflict, range)
								startIndex = range.last + 1
								if (range.last > block.leftTo) {
									blockIndex++
								}
								return@findConflict true
							}

							blockIndex++
						}

						return@findConflict false
					}
				}
			}
		}

		private fun findConflict(lines: ResolverLines, startIndex: Int, conflict: ResolverConflict, version: Version, consume: (IntRange) -> Boolean) {
			val conflictLines = conflict[version]
			for (i in startIndex..lines.size - conflictLines.size) {
				if (lines.subList(i, i + conflictLines.size) == conflictLines) {
					if (consume(IntRange(i, i + conflictLines.size - 1))) {
						return
					}
				}
			}
		}

		private fun createContent(lines: ResolverLines, range: IntRange, context: Int): Triple<ResolverSnippet.Content, Int, Int> {
			val conflictFrom = range.first
			val conflictTo = range.last
			val snippetFrom = max(0, conflictFrom - context)
			val snippetTo = min(lines.size, conflictTo + 1 + context)
			val beforeBuilder = StringBuilder()
			for (i in snippetFrom until conflictFrom) {
				beforeBuilder.append(lines[i])
				beforeBuilder.append("\n")
			}

			while (beforeBuilder.startsWith("\n")) {
				beforeBuilder.deleteCharAt(0)
			}

			val textBuilder = StringBuilder()
			for (i in conflictFrom..conflictTo) {
				textBuilder.append(lines[i])
				textBuilder.append("\n")
			}

			val afterBuilder = StringBuilder()
			for (i in conflictTo + 1 until snippetTo) {
				afterBuilder.append(lines[i])
				afterBuilder.append("\n")
			}

			while (afterBuilder.endsWith("\n\n")) {
				afterBuilder.deleteCharAt(afterBuilder.length - 1)
			}

			val content = ResolverSnippet.Content(beforeBuilder.toString(), textBuilder.toString(), afterBuilder.toString())
			return Triple(content, lines.offset(snippetFrom), lines.offset(snippetTo))
		}

		private fun reduceContent(draftContent: String, conflicts: List<ResolverConflict>): String {
			return conflicts.asReversed().fold(draftContent) { reduced, conflict ->
				reduced.replaceRange(conflict.range, "")
			}
		}

		private fun createDiff(baseLines: ResolverLines, oursLines: ResolverLines): Diff {
			val blocks = createBlocks(baseLines, oursLines)
			return Diff(baseLines, oursLines, blocks)
		}

		@Suppress("UNCHECKED_CAST")
		private fun createBlocks(leftLines: ResolverLines, rightLines: ResolverLines): List<SequenceBlock> {
			val lineMedia = SequenceMedia(leftLines, rightLines)
			val cachingMedia = QSequenceCachingMedia(lineMedia, QSequenceDummyCanceller())
			val discardingMedia = QSequenceDiscardingMedia(cachingMedia, QSequenceDiscardingMediaNoConfusionDectector(true), QSequenceDummyCanceller())
			val blocks = (QSequenceDifference(discardingMedia, discardingMedia, Integer.MAX_VALUE).blocks as List<QSequenceDifferenceBlock>).map { SequenceBlock(it.leftFrom, it.leftTo + 1, it.rightFrom, it.rightTo + 1) }
			return blocks
		}
	}

	private class Ids {
		private val digest = MessageDigest.getInstance("SHA-256")

		fun createId(base: String, ours: String, oursFrom: Int, theirs: String, theirsFrom: Int): ResolverId {
			// We define the conflict-id based on the immutable input files, so changes to the working tree file won't affect the ID
			digest.reset()
			digest.update(base.toByteArray())
			digest.update(ours.toByteArray())
			digest.update(theirs.toByteArray())
			digest.update(oursFrom.toString().toByteArray()) // to make IDs unique even if we have the same conflict several times
			digest.update(theirsFrom.toString().toByteArray()) // to make IDs unique even if we have the same conflict several times
			return ResolverId(BigInteger(1, digest.digest()).toString(16))
		}
	}


	private class Diff(val leftLines: ResolverLines, val rightLines: ResolverLines, val blocks: List<SequenceBlock>) {
		fun length(side: Side): Int {
			return when (side) {
				Side.LEFT -> leftLines.size
				Side.RIGHT -> rightLines.size
			}
		}
	}

	enum class Version {
		OURS, THEIRS
	}

	private class SequenceMedia(val leftLines: ResolverLines, val rightLines: ResolverLines) : QSequenceCachableMedia {
		override fun equals(p0: Int, p1: Int): Boolean {
			return leftLines[p0] == rightLines[p1]
		}

		override fun getLeftLength(): Int {
			return leftLines.size
		}

		override fun getRightLength(): Int {
			return rightLines.size
		}

		override fun getMediaLeftObject(p0: Int): Any {
			return leftLines[p0]
		}

		override fun getMediaRightObject(p0: Int): Any {
			return rightLines[p0]
		}
	}
}
