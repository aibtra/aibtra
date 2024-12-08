package dev.aibtra.main.resolver

import dev.aibtra.diff.*

class ResolveSummary(val content: ResolverSummaryContent, val diffChars: List<DiffChar>) {
	init {
		require(content.text.length == diffChars.size)
	}
}