package dev.aibtra.main.resolver

import dev.aibtra.diff.*
import dev.aibtra.resolver.*

class ResolverDiff(val draft: ResolverText, val resolution: ResolverResolution, val draftChars: List<DiffChar>, val resolutionChars: List<DiffChar>, val blocks: List<DiffBlock>) {

	init {
		require(draft.text.length == draftChars.size)
		require(resolution.content.join(false).length == resolutionChars.size)
	}

	companion object {
		fun build(text: ResolverText, resolution: ResolverResolution): ResolverDiff {
			val draftText = text.text
			val resolutionText = resolution.content.join(false)
			val blocks = DiffBuilder(draftText, resolutionText, true, true, true, DiffTokenizingMode.ALPHANUMERIC).build()
			val orgDiff = Diff.OrgDiff(draftText, resolutionText, resolutionText, draftText.length, resolutionText.length, blocks)
			val diff = Diff(draftText, resolutionText, blocks, orgDiff, true)
			val draftChars = DiffFormatter(DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED).format(diff).second
			val resolutionChars = DiffFormatter(DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED).format(diff).second
			return ResolverDiff(text, resolution, draftChars, resolutionChars, blocks)
		}
	}
}