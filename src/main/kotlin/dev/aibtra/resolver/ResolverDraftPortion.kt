package dev.aibtra.resolver

data class ResolverDraftPortion(
	val file: ResolverFile,
	val start: Int,
	val end: Int
) {
	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}

	companion object {
		fun applyReplacements(portionToReplacement: Map<ResolverDraftPortion, String>): Map<ResolverFile, ResolverFile> {
			val fileToPortion = portionToReplacement.keys.groupBy { it.file }
			val fileToReplacement = mutableMapOf<ResolverFile, ResolverFile>()
			for ((file, portions) in fileToPortion) {
				val sorted = portions.sortedBy { it.start }.reversed()
				val draft = sorted.fold(file.draftContent) { acc, portion ->
					acc.substring(0, portion.start) + portionToReplacement[portion] + acc.substring(portion.end)
				}
				fileToReplacement[file] = ResolverFile(file.name, file.lastModifiedTime, file.draftPathRel, draft, file.draftEol, file.baseContent, file.oursContent, file.theirsContent)
			}
			return fileToReplacement
		}
	}
}