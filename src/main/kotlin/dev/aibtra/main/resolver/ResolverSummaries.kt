package dev.aibtra.main.resolver

import dev.aibtra.diff.*

class ResolverSummaries(val drafts: ResolveSummary, val resolutions: ResolveSummary, val scrollDiffBlocks: List<DiffBlock>) {

	companion object {
		fun build(diffs: ResolverDiffs): ResolverSummaries {
			val draftBuilder = StringBuilder()
			val resolutionBuilder = StringBuilder()
			val draftHeaders = mutableListOf<ResolverSummaryContent.Header>()
			val resolutionHeaders = mutableListOf<ResolverSummaryContent.Header>()
			val draftAllChars = mutableListOf<DiffChar>()
			val resolutionAllChars = mutableListOf<DiffChar>()
			val scrollDiffBlocks = mutableListOf<DiffBlock>()
			for (snippet in diffs.snippets) {
				val draftChars = diffs.getDraftChars(snippet)
				val draftText = diffs.texts[snippet]
				val draftString = draftText.text
				val resolutionChars = diffs.getResolutionChars(snippet)
				val resolution = diffs.resolutions?.get(snippet)
				val resolutionContent = if (resolutionChars != null) requireNotNull(resolution).content else null
				val resolutionWithPrefixSuffix = resolution?.prefixSuffixFound ?: true

				// We "highlight" only the space, not the \n, but block updates to the entire line;
				// this solves the problem of Swing extending our highlighting which would occur
				// if we would allow insertions immediately after the highlight.
				val draftHeaderStart = draftBuilder.length
				val resolutionHeaderStart = resolutionBuilder.length
				appendDraftPlainText(" \n", draftBuilder, draftAllChars, resolutionHeaderStart)

				val draftContentStart = draftBuilder.length
				val draftRange = IntRange(draftContentStart, draftContentStart + draftString.length)
				val conflictRange = draftText.conflictRange?.let { IntRange(draftRange.first + it.first, draftRange.first + it.last) }
				draftHeaders.add(ResolverSummaryContent.Header(snippet, draftHeaderStart, draftRange, conflictRange))

				resolutionContent?.let {
					val start = it.before.length
					val end = it.before.length + it.text.length - 1
					appendResolutionPlainText(" \n", resolutionBuilder, resolutionAllChars, draftHeaderStart)

					val resolutionContentStart = resolutionBuilder.length
					val resolutionRange = IntRange(resolutionContentStart, resolutionContentStart + it.before.length + it.text.length + it.after.length)
					val mappedConflictRangeRaw = conflictRange?.let { IntRange(resolutionRange.first + start, resolutionRange.first + end) }
					val mappedConflictRange = if (resolutionWithPrefixSuffix && start < end) mappedConflictRangeRaw else null
					resolutionHeaders.add(ResolverSummaryContent.Header(snippet, resolutionHeaderStart, resolutionRange, mappedConflictRange))
				}

				val resolutionContentStart = resolutionBuilder.length
				draftBuilder.append(draftString)
				draftChars?.let {
					for (char in it) {
						draftAllChars.add(
							char.copy(
								block = null,
								posRaw = char.posRaw + draftContentStart,
								posRef = char.posRef + resolutionContentStart
							)
						)
					}
				} ?: run {
					repeat(draftString.length) {
						draftAllChars.add(DiffChar(DiffKind.EQUAL, null, draftAllChars.size - 1, resolutionContentStart))
					}
				}

				appendDraftPlainText("\n", draftBuilder, draftAllChars, resolutionContentStart)

				resolutionContent?.let { content ->
					val string = content.join(false)
					resolutionBuilder.append(string)

					resolutionChars?.let {
						for (char in it) {
							resolutionAllChars.add(
								char.copy(
									block = null,
									posRaw = char.posRaw + draftContentStart,
									posRef = char.posRef + resolutionContentStart
								)
							)
						}
					} ?: run {
						repeat(string.length) {
							resolutionAllChars.add(DiffChar(DiffKind.EQUAL, null, resolutionAllChars.size - 1, draftContentStart))
						}
					}

					appendResolutionPlainText("\n", resolutionBuilder, resolutionAllChars, draftContentStart)
					scrollDiffBlocks.add(DiffBlock(draftHeaderStart, draftBuilder.length, resolutionHeaderStart, resolutionBuilder.length))
				} ?: run {
					scrollDiffBlocks.add(DiffBlock(draftHeaderStart, draftBuilder.length, resolutionHeaderStart, resolutionHeaderStart))
				}
			}

			val draftContent = ResolverSummaryContent(draftBuilder.toString(), draftHeaders)
			val resolutionContent = ResolverSummaryContent(resolutionBuilder.toString(), resolutionHeaders)
			val resolveSummary = ResolveSummary(draftContent, draftAllChars)
			val resolutionSummary = ResolveSummary(resolutionContent, resolutionAllChars)
			return ResolverSummaries(resolveSummary, resolutionSummary, scrollDiffBlocks)
		}

		private fun appendDraftPlainText(text: String, builder: StringBuilder, chars: MutableList<DiffChar>, resolutionOffsetExcl: Int) {
			builder.append(text)
			repeat(text.length) {
				chars.add(DiffChar(DiffKind.EQUAL, null, chars.size - 1, resolutionOffsetExcl - 1))
			}
		}

		private fun appendResolutionPlainText(text: String, builder: StringBuilder, chars: MutableList<DiffChar>, draftOffsetExcl: Int) {
			builder.append(text)
			repeat(text.length) {
				chars.add(DiffChar(DiffKind.EQUAL, null, draftOffsetExcl - 1, chars.size - 1))
			}
		}
	}
}