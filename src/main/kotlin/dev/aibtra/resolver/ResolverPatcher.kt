package dev.aibtra.resolver

import dev.aibtra.core.*
import kotlin.math.*

class ResolverPatcher {
	companion object {
		fun apply(patchString: String, snippet: ResolverSnippet, fuzzyRange: FuzzyRange): ResolverResolution {
			val patchLines = LineTokenizing.tokenize(patchString)
			val draftAfterLines = splitLines(snippet.draft.after)

			var resLines = patchLines
			var resAfterLines: LineTokenizing? = null
			findAfterLines(resLines, draftAfterLines, fuzzyRange)?.let {
				resAfterLines = draftAfterLines
				resLines = resLines.subTokenizing(0 until it)
			} ?: findAfterOverlap(resLines, draftAfterLines)?.let {
				resLines = resLines.subTokenizing(0 until it)
			}

			val draftBeforeLines = splitLines(snippet.draft.before)
			var resBeforeLines: LineTokenizing? = null
			findBeforeLines(resLines, draftBeforeLines, fuzzyRange)?.let {
				resBeforeLines = draftBeforeLines
				resLines = resLines.subTokenizing(it + 1 until resLines.size)
			} ?: findBeforeOverlap(resLines, draftBeforeLines)?.let {
				resLines = resLines.subTokenizing(it + 1 until resLines.size)
			}

			extendPatch(resLines, resBeforeLines, resAfterLines, snippet)?.let {
				return it
			}

			val before = joinLines(resBeforeLines)
			val prefixSuffixFound = resBeforeLines != null && resAfterLines != null
			val text = if (prefixSuffixFound) {
				fixIndentation(resLines, snippet.conflict)
			}
			else {
				joinLines(resLines)
			}

			val after = joinLines(resAfterLines)
			val resolved = ResolverSnippet.Content(before, text, after)
			return ResolverResolution(snippet, resolved, prefixSuffixFound)
		}

		private fun extendPatch(patchLines: LineTokenizing, patchBeforeLines: LineTokenizing?, patchAfterLines: LineTokenizing?, snippet: ResolverSnippet): ResolverResolution? {
			if (patchBeforeLines != null || patchAfterLines != null) {
				return null
			}

			val conflict = snippet.conflict
			val minConflictSize = min(conflict.ours.size, conflict.theirs.size)
			val draftBefore = snippet.draft.before
			val draftAfter = snippet.draft.after
			val contextSize = splitLines(draftBefore).size + splitLines(draftAfter).size
			if (patchLines.size > minConflictSize + contextSize / 2) {
				return null
			}

			val text = fixIndentation(patchLines, snippet.conflict)
			val resolved = ResolverSnippet.Content(draftBefore, text, draftAfter)
			return ResolverResolution(snippet, resolved, true)
		}

		private fun findAfterLines(patch: LineTokenizing, draftAfter: LineTokenizing, fuzzyRange: FuzzyRange): Int? {
			// Sometimes o1-mini-2024-09-12 reports the entire snippet, but with the last line changed
			val draftReduced = if (draftAfter.size > fuzzyRange.minLineCount) draftAfter.subTokenizing(0 until draftAfter.size - fuzzyRange.ignoreLineCount) else draftAfter

			// Sometimes o1-mini-2024-09-12 decides to extend the give snippet with new lines
			return patch.firstIndexOf(draftReduced, true)?.let {
				return it
			}
		}

		private fun findAfterOverlap(patch: LineTokenizing, draftAfter: LineTokenizing): Int? {
			// Sometimes o1-mini-2024-09-12 chooses to send a few more lines after the conflict from the original snippet
			val patchEnd = if (patch.string(patch.size - 1, false, false).isEmpty()) {
				patch.size - 2
			}
			else {
				patch.size - 1
			}

			for (overlap in min(draftAfter.size - 1, patchEnd) downTo 0) {
				if (draftAfter.equals(patch, 0..overlap, patchEnd - overlap..patchEnd, true)) {
					return patchEnd - overlap
				}
			}

			return null
		}

		private fun findBeforeLines(patch: LineTokenizing, draftBefore: LineTokenizing, fuzzyRange: FuzzyRange): Int? {
			val draftReduced = if (draftBefore.size > fuzzyRange.minLineCount) draftBefore.subTokenizing(fuzzyRange.ignoreLineCount until draftBefore.size) else draftBefore
			return patch.lastIndexOf(draftReduced, true)?.let {
				return it + draftReduced.size - 1
			}
		}

		private fun findBeforeOverlap(patch: LineTokenizing, draftBefore: LineTokenizing): Int? {
			val draftEnd = draftBefore.size - 1
			for (overlap in min(draftEnd, patch.size - 1) downTo 0) {
				if (draftBefore.equals(patch, draftEnd - overlap..draftEnd, 0..overlap, true)) {
					return overlap
				}
			}

			return null
		}

		private fun fixIndentation(tokenizing: LineTokenizing, conflict: ResolverConflict): String {
			val rawLines = tokenizing.indices().map { tokenizing.string(it, false, false) }
			return joinLines(StringUtils.fixIndentation(rawLines, conflict.ours + conflict.theirs))
		}

		private fun splitLines(patch: String): LineTokenizing {
			return LineTokenizing.tokenize(patch.removeSuffix("\n"))
		}

		private fun joinLines(tokenizing: LineTokenizing?): String {
			return canonicalizeEnd(tokenizing?.string() ?: "")
		}

		private fun joinLines(lines: List<String>): String {
			return canonicalizeEnd(lines.joinToString("\n"))
		}

		private fun canonicalizeEnd(text: String): String {
			return if (text.isNotEmpty() && !text.endsWith("\n")) text + "\n" else text
		}
	}

	data class FuzzyRange(val minLineCount: Int, val ignoreLineCount: Int) {
		init {
			require(minLineCount > ignoreLineCount)
		}
	}
}