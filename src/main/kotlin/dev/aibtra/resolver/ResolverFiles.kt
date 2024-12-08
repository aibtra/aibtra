package dev.aibtra.resolver

import dev.aibtra.core.*
import java.io.*
import java.nio.file.*
import kotlin.io.path.*

data class ResolverFiles(val repoRoot: Path, val files: List<ResolverFile>, val overviewFile: Path) {
	override fun equals(other: Any?): Boolean {
		return this === other
	}

	override fun hashCode(): Int {
		return System.identityHashCode(this)
	}

	companion object {
		fun parseOverviewFile(overviewFile: Path, maxCount: Int = Int.MAX_VALUE): ResolverFiles {
			val draftFiles = mutableListOf<ResolverFile>()
			if (!Files.exists(overviewFile)) {
				throw IOException("Input file does not exist: $overviewFile")
			}

			val parentPath = overviewFile.parent
			var count = 0
			Files.lines(overviewFile).use { linesStream ->
				val lines = linesStream.toList()
				val repoRoot = Path.of(lines.firstOrNull() ?: throw IOException("Invalid overview file: missing initial repository root path"))
				if (!Files.isDirectory(repoRoot)) {
					throw IOException("Invalid overview file: repository root $repoRoot does not exist")
				}

				for (line in lines.subList(1, lines.size)) {
					val paths = line.trim().split("\t")
					if (paths.size != 4) {
						throw IOException("Invalid overview file: invalid path line '$line'")
					}

					val draftPathRel = paths[0]
					val basePathRel = paths[1]
					val oursPathRel = paths[2]
					val theirsPathRel = paths[3]
					val draftPath = repoRoot.resolve(draftPathRel)
					val name = draftPath.fileName.toString()
					val lastModifiedTime = draftPath.getLastModifiedTime()
					val (draftContent, draftEol) = readContentWithLineEndings(draftPath).let { (content, eol) -> Pair(content, eol ?: throw IOException("Files with mixed line endings can't be processed!")) }
					val baseContent = readContent(parentPath.resolve(basePathRel))
					val oursContent = readContent(parentPath.resolve(oursPathRel))
					val theirsContent = readContent(parentPath.resolve(theirsPathRel))
					val draftFile = ResolverFile(name, lastModifiedTime, draftPathRel, draftContent, draftEol, baseContent, oursContent, theirsContent)
					if (count++ < maxCount) {
						draftFiles.add(draftFile)
					}
				}

				return ResolverFiles(repoRoot, draftFiles, overviewFile)
			}
		}

		private fun readContent(path: Path): String {
			return normalizeContent(readFileContent(path))
		}

		private fun readContentWithLineEndings(path: Path): Pair<String, StringUtils.Eol?> {
			val content = readFileContent(path)
			val eol = StringUtils.determineLineEnding(content)
			return Pair(normalizeContent(content), eol)
		}

		private fun normalizeContent(readFileContent: String): String {
			return readFileContent.replace("\r\n", "\n").replace("\r", "\n")
		}

		private fun readFileContent(path: Path): String {
			if (!Files.exists(path)) {
				throw IOException("File does not exist: $path")
			}
			return Files.readString(path)!!
		}
	}
}
