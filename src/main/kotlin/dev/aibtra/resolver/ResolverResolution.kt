package dev.aibtra.resolver

data class ResolverResolution(
	val snippet: ResolverSnippet,
	val content: ResolverSnippet.Content,
	val prefixSuffixFound: Boolean
) {
	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}
}