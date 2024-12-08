package dev.aibtra.resolver

class ResolverResolutions(val snippets: ResolverSnippets, private val list: List<ResolverResolution>, val resolverPacket: ResolverPacket) : Iterable<ResolverResolution> {
	private val snippetToResolution: Map<ResolverSnippet, ResolverResolution>

	init {
		require(snippets.toSet().containsAll(list.map { it.snippet }))

		snippetToResolution = list.associateBy { it.snippet }
	}

	override fun iterator(): Iterator<ResolverResolution> {
		return list.iterator()
	}

	operator fun get(snippet: ResolverSnippet): ResolverResolution? {
		return snippetToResolution[snippet]
	}

	fun replaceSnippets(snippets: ResolverSnippets): ResolverResolutions {
		val idToSnippet = snippets.associateBy { it.id }
		val transformed = list.mapNotNull { resolution ->
			idToSnippet[resolution.snippet.id]?.let { snippet ->
				resolution.copy(snippet = snippet)
			}
		}
		return ResolverResolutions(snippets, transformed, resolverPacket)
	}
}