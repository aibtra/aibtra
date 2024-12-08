package dev.aibtra.resolver

data class ResolverConflict(
	val range: IntRange, // inclusive, pointing to the final \n
	val ours: List<String>,
	val theirs: List<String>
) {
	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}

	operator fun get(version: ResolverSnippets.Version): List<String> {
		return when (version) {
			ResolverSnippets.Version.OURS -> ours
			ResolverSnippets.Version.THEIRS -> theirs
		}
	}

	companion object {
		private val CONFLICT_PATTERN = Regex("""(?s)<<<<<<< (.*?)\n=======\n(.*?)>>>>>>> .*?(?:\n|$)""")

		fun extract(content: String): List<ResolverConflict> {
			return CONFLICT_PATTERN.findAll(content).mapNotNull { result ->
				val ours = result.groups[1]?.value?.substringAfter('\n', "")
				val theirs = result.groups[2]?.value?.substringBeforeLast('\n', "")
				val range = result.range
				if (ours != null && theirs != null) {
					val ourLines = if (ours.isNotEmpty()) ResolverLines(ours).subList(0) else listOf()
					val theirLines = if (theirs.isNotEmpty()) ResolverLines(theirs).subList(0) else listOf()
					ResolverConflict(range, ourLines, theirLines)
				}
				else {
					null
				}
			}.toList()
		}
	}
}