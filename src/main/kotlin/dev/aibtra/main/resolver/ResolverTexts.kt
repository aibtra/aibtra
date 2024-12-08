package dev.aibtra.main.resolver

import dev.aibtra.resolver.*

class ResolverTexts(val snippets: ResolverSnippets, private val list: List<ResolverText>) : Iterable<ResolverText> {
	private val snippetToText: Map<ResolverSnippet, ResolverText>

	init {
		require(snippets.toSet() == list.map { it.snippet }.toSet())

		snippetToText = list.associateBy { it.snippet }
	}

	override fun iterator(): Iterator<ResolverText> {
		return list.iterator()
	}

	operator fun get(snippet: ResolverSnippet): ResolverText {
		return requireNotNull(snippetToText[snippet])
	}

	fun equalsContent(other: ResolverTexts): Boolean {
		return list.map { it.text } == other.list.map { it.text }
	}

	companion object {
		fun createFrom(snippets: ResolverSnippets) : ResolverTexts {
			val texts = snippets.map { createText(it) }
			return ResolverTexts(snippets, texts)
		}

		fun getModified(texts: ResolverTexts): List<ResolverText> {
			return texts.filter {
				it.text != createText(it.snippet).text
			}
		}

		private fun createText(it: ResolverSnippet): ResolverText {
			return ResolverText(it, it.draft.join(false))
		}
	}
}