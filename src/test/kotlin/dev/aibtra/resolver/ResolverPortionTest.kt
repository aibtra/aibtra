package dev.aibtra.resolver

import dev.aibtra.core.*
import org.junit.jupiter.api.*
import java.nio.file.attribute.*

class ResolverPortionTest {

	@Test
	fun testSingleFileSingleReplacement() {
		val file1 = file("This\nIS\na test")
		assert(
			listOf(
				Replacement(file1, "IS", "are")
			), mapOf(
				file1 to "This\nare\na test"
			)
		)
	}

	@Test
	fun testSingleFileTwoReplacement() {
		val file1 = file("This\nIS\na TEST")
		assert(
			listOf(
				Replacement(file1, "IS\n", "are "),
				Replacement(file1, "TEST", "tests")
			), mapOf(
				file1 to "This\nare a tests"
			)
		)
	}

	@Test
	fun testSingleFileThreeReplacement() {
		val file1 = file("This\nIS\nA TEST")
		assert(
			listOf(
				Replacement(file1, "\nIS\n", " are "),
				Replacement(file1, "A ", ""),
				Replacement(file1, "TEST", "tests"),
			), mapOf(
				file1 to "This are tests"
			)
		)
	}

	@Test
	fun testTwoFilesOneReplacement() {
		val file1 = file("THIS is test 1")
		val file2 = file("This IS test 2")
		val file3 = file("This is TEST 3")
		assert(
			listOf(
				Replacement(file1, "THIS", "This"),
				Replacement(file2, " IS ", " is "),
				Replacement(file3, " TEST", " test"),
			), mapOf(
				file1 to "This is test 1",
				file2 to "This is test 2",
				file3 to "This is test 3"
			)
		)
	}

	@Test
	fun testThreeFilesFiveReplacement() {
		val file1 = file("THIS IS TEST 1")
		val file2 = file("This IS test 2")
		val file3 = file("This is TEST 3")
		assert(
			listOf(
				Replacement(file1, "THIS", "This"),
				Replacement(file1, " IS", " is"),
				Replacement(file1, "TEST", "test"),
				Replacement(file2, " IS ", " is "),
				Replacement(file3, " TEST", " test"),
			), mapOf(
				file1 to "This is test 1",
				file2 to "This is test 2",
				file3 to "This is test 3"
			)
		)
	}

	private fun file(content: String): ResolverFile {
		return ResolverFile(
			"draft", FileTime.fromMillis(0),
			"draft", content, StringUtils.Eol.UNIX,
			"",
			"",
			""
		)
	}

	private fun assert(replacements: List<Replacement>, fileToExpectedContent: Map<ResolverFile, String>) {
		val portionToReplacement = mutableMapOf<ResolverDraftPortion, String>()
		for (replacement in replacements) {
			val content = replacement.file.draftContent
			val index = content.indexOf(replacement.old)
			require(index >= 0 && content.indexOf(replacement.old, index + 1) == -1)

			portionToReplacement[ResolverDraftPortion(replacement.file, index, index + replacement.old.length)] = replacement.new
		}

		val fileToReplacement = ResolverDraftPortion.applyReplacements(portionToReplacement)
		for (file in fileToExpectedContent.keys) {
			Assertions.assertEquals(fileToExpectedContent[file], fileToReplacement[file]!!.draftContent)
		}
	}

	private data class Replacement(val file: ResolverFile, val old: String, val new: String)
}