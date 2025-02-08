package dev.aibtra.resolver

import dev.aibtra.core.*
import kotlin.math.*

class ResolverPatcher {
	companion object {
		fun apply(patchString: String, snippet: ResolverSnippet): ResolverResolution {
			val patchLines = LineTokenizing.tokenize(patchString)
			val draftAfterLines = splitLines(snippet.draft.after)

			var resLines = patchLines
			findAfterOverlap(resLines, draftAfterLines)?.let {
				resLines = resLines.subTokenizing(0 until it)
			}

			val draftBeforeLines = splitLines(snippet.draft.before)
			findBeforeOverlap(resLines, draftBeforeLines)?.let {
				resLines = resLines.subTokenizing(it + 1 until resLines.size)
			}

			val text = fixIndentation(resLines, snippet.conflict)
			val resolved = ResolverSnippet.Content(snippet.draft.before, text, snippet.draft.after)
			return ResolverResolution(snippet, resolved, true)
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

		private fun joinLines(lines: List<String>): String {
			return canonicalizeEnd(lines.joinToString("\n"))
		}

		private fun canonicalizeEnd(text: String): String {
			return if (text.isNotEmpty() && !text.endsWith("\n")) text + "\n" else text
		}
	}
}