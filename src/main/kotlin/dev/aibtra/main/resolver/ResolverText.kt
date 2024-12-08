package dev.aibtra.main.resolver

import dev.aibtra.resolver.*

class ResolverText(val snippet: ResolverSnippet, val text: String) {

	val conflictRange: IntRange?

	init {
		text.matches(CONFLICT_REGEX)

		conflictRange = CONFLICT_REGEX.find(text)?.range
	}

	companion object {
		val CONFLICT_REGEX = Regex("""(?s)<<<<<<< .+?\n.*?>>>>>>> .+?(\n|$)""")
	}
}