package dev.aibtra.resolver

import dev.aibtra.core.*
import java.nio.file.attribute.*

data class ResolverFile(
	val name: String,
	val lastModifiedTime: FileTime,
	val draftPathRel: String, val draftContent: String, val draftEol: StringUtils.Eol,
	val baseContent: String,
	val oursContent: String,
	val theirsContent: String,
) {
	init {
		require(!baseContent.contains("\r"))
		require(!oursContent.contains("\r"))
		require(!theirsContent.contains("\r"))
		require(!draftContent.contains("\r"))
	}

	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}
}