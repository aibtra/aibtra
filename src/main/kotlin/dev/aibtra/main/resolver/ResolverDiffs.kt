package dev.aibtra.main.resolver

import dev.aibtra.diff.*
import dev.aibtra.resolver.*

class ResolverDiffs private constructor(val snippets: ResolverSnippets, val texts: ResolverTexts, val resolutions: ResolverResolutions?, val map: Map<ResolverSnippet, ResolverDiff?>) {
	private val idToDiff: Map<ResolverId, ResolverDiff?>

	init {
		val set = snippets.toSet()
		require(map.keys == set)
		require(texts.snippets.toSet() == set)
		resolutions?.let {
			require(it.snippets.toSet() == set)
		}

		require(texts.toSet().containsAll(map.values.mapNotNull { it?.draft }.toSet()))

		val ids = mutableSetOf<ResolverId>()
		idToDiff = map.map { (snippet, diff) ->
			require(ids.add(snippet.id))
			Pair(snippet.id, diff)
		}.associate { Pair(it.first, it.second) }
	}

	operator fun get(id: ResolverId): ResolverDiff {
		return requireNotNull(idToDiff[id])
	}

	fun getDraftChars(snippet: ResolverSnippet): List<DiffChar>? {
		return map[snippet]?.draftChars
	}

	fun getResolutionChars(snippet: ResolverSnippet): List<DiffChar>? {
		return map[snippet]?.resolutionChars
	}

	companion object {
		fun build(texts: ResolverTexts, resolutions: ResolverResolutions?): ResolverDiffs {
			val snippets = texts.snippets
			val snippetToDiff = snippets.associateWith {
				val text = texts[it]
				resolutions?.let { res ->
					res[it]?.let { r ->
						ResolverDiff.build(text, r)
					}
				}
			}

			return ResolverDiffs(snippets, texts, resolutions, snippetToDiff)
		}
	}
}