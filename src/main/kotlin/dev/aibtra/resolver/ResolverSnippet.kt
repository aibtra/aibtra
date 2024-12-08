package dev.aibtra.resolver

// We don't want structural equality!
data class ResolverSnippet(
	val id: ResolverId,
	val file: ResolverFile,
	val conflict: ResolverConflict,
	val draft: Content,
	val base: Content,
	val ours: Content,
	val theirs: Content,
	val draftPortion: ResolverDraftPortion,
	val warning: String?
) {
	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}

	fun toDebugString(): String {
		val indent = " ".repeat(2)
		val header = """
			CONFLICT-ID: $id
			FILENAME: ${file.draftPathRel}
		""".trimIndent()

		return header + "\n" +
						"DRAFT:\n" +
						draft.join(true).prependIndent(indent) + "\n" +
						"BASE:\n" +
						base.join(true).prependIndent(indent) + "\n" +
						"OURS:\n" +
						ours.join(true).prependIndent(indent) + "\n" +
						"THEIRS:\n" +
						theirs.join(true).prependIndent(indent)
	}

	class Content(val before: String, val text: String, val after: String) {
		init {
			require(before.isEmpty() || before.endsWith("\n"))
			require(text.isEmpty() || text.endsWith("\n"))
			require(after.isEmpty() || after.endsWith("\n"))
		}

		fun join(trailingNewLine: Boolean): String {
			val joint = before + text + after
			return if (trailingNewLine || joint.isEmpty()) {
				joint
			}
			else {
				joint.substring(0, joint.length - 1)
			}
		}
	}
}