package dev.aibtra.main.resolver

import dev.aibtra.core.*
import dev.aibtra.resolver.*

class ResolverSummaryContent(val text: String, val headers: List<Header>) {
	init {
		require(headers.isSortedStrictly { o1, o2 -> o1.offset.compareTo(o2.offset) })
		require(headers.all { text[it.offset] == ' ' && text[it.offset + 1] == '\n' })
	}

	class Header(val snippet: ResolverSnippet, val offset: Int, val contentRange: IntRange, val conflictRange: IntRange?)

	fun split(snippets: ResolverSnippets): ResolverTexts {
		return ResolverTexts(snippets, headers.indices.map { i ->
			val header = headers[i]
			val next = headers.getOrNull(i + 1)
			val from = header.offset + 2
			val to = next?.offset ?: text.length
			ResolverText(header.snippet, text.substring(from, to - 1)) // -1 because we always add a trailing \n
		})
	}

	fun getSnippetRange(snippet: ResolverSnippet): IntRange? {
		for (header in headers) {
			if (header.snippet == snippet) {
				return header.contentRange
			}
		}

		return null
	}

	fun getHeader(snippet: ResolverSnippet): Header? {
		for (header in headers) {
			if (header.snippet == snippet) {
				return header
			}
		}

		return null
	}

	fun findSnippetAt(position: Int): ResolverSnippet? {
		for (header in headers) {
			if (header.contentRange.contains(position)) {
				return header.snippet
			}
		}

		return null
	}

	fun findConflictAt(position: Int) : ResolverSnippet? {
		for (header in headers) {
			if (header.conflictRange?.contains(position) == true) {
				return header.snippet
			}
		}

		return null
	}
}