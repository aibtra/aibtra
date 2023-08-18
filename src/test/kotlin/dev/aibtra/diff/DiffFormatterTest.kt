/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DiffFormatterTest {

	@Test
	fun testEqual() {
		assert(
			"Everything is equal.",
			"                    ",
			"Everything is equal.",
			"                    ",
			"                    "
		)
	}

	@Test
	fun testRefEmpty() {
		assert(
			"Some text yet to be processed",
			"-----------------------------",
			"",
			"-----------------------------",
			""
		)
	}

	@Test
	fun testInsertion() {
		assert(
			"Ther is a typo.",
			"   ><          ",
			"There is a typo.",
			"    +           ",
			"    +           "
		)
	}

	@Test
	fun testInsertionAtStart() {
		assert(
			"There is a typo.",
			"<               ",
			"xThere is a typo.",
			"+                ",
			"+                "
		)
	}

	@Test
	fun testInsertionAtEnd() {
		assert(
			"There is a typo.",
			"               >",
			"There is a typo.x",
			"                +",
			"                +"
		)
	}

	@Test
	fun testRemoval() {
		assert(
			"Thereeee is a typo.",
			"     ---           ",
			"There is a typo.",
			"     ---           ",
			"    ><          "
		)
	}

	@Test
	fun testRemovalAtStart() {
		assert(
			"xThere is a typo.",
			"-                ",
			"There is a typo.",
			"-                ",
			"<               "
		)
	}

	@Test
	fun testRemovalAtEnd() {
		assert(
			"There is a typo.x",
			"                -",
			"There is a typo.",
			"                -",
			"               >"
		)
	}

	@Test
	fun testRemovalInProgress1() {
		assert(
			"Theree is a typo.",
			"     ~~~         ",
			"There is",
			"     ---+++",
			"     ~~~",
			rawTo = 8
		)
	}

	@Test
	fun testRemovalInProgress2() {
		assert(
			"Theree is a typo.",
			"     -           ",
			"There is",
			"     -   ",
			"    ><  ",
			rawTo = 9
		)
	}

	@Test
	fun testRemovalInProgress3() {
		assert(
			"Theree is a typo.",
			"     -           ",
			"There is ",
			"     -    ",
			"    ><   ",
			rawTo = 10
		)
	}

	private fun assert(raw: String, rawCharsExpected: String, ref: String, refFullCharsExpected: String, refKeptCharsExpected: String, rawTo: Int = raw.length) {
		val blocks = DiffBuilder(raw.substring(0, rawTo), ref, true, true, true).build()
		val rawFormatted = DiffFormatter(DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED).format(raw, rawTo, ref, blocks)
		val rawCharsActual = formatChars(raw, ref, rawFormatted.second, DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED)
		val refFullFormatted = DiffFormatter(DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED).format(raw, rawTo, ref, blocks)
		val refFullCharsActual = formatChars(raw, ref, refFullFormatted.second, DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED)
		val refKeptFormatted = DiffFormatter(DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED).format(raw, rawTo, ref, blocks)
		val refKeptCharsActual = formatChars(raw, ref, refKeptFormatted.second, DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED)

		Assertions.assertEquals(
			format(raw, rawCharsExpected, ref, refFullCharsExpected, refKeptCharsExpected),
			format(raw, rawCharsActual, ref, refFullCharsActual, refKeptCharsActual)
		)
	}

	private fun formatChars(rawText: String, refText: String, chars: List<DiffChar>, mode: DiffFormatter.Mode): String {
		assertChars(rawText, refText, chars, mode)

		val text = StringBuilder()
		for (char in chars) {
			text.append(char.kind.char)
		}

		return text.toString()
	}

	private fun format(rawText: String, rawChars: String, refFullText: String, refFullChars: String, refKeptChars: String): String {
		val text = StringBuilder()
		text.append("\"$rawText\",\n")
		text.append("\"$rawChars\",\n")
		text.append("\"$refFullText\",\n")
		text.append("\"$refFullChars\",\n")
		text.append("\"$refKeptChars\"\n")
		return text.toString()
	}

	private fun assertChars(rawText: String, refText: String, chars: List<DiffChar>, mode: DiffFormatter.Mode) {
		when (mode) {
			DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED -> Assertions.assertEquals(rawText.length, chars.size)
			DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED -> Assertions.assertEquals(refText.length, chars.size)
			DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED -> {}
		}

		var raw = -1
		var ref = -1
		for (char in chars) {
			when (mode) {
				DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED -> {
					Assertions.assertTrue(char.posRaw == raw || char.posRaw == raw + 1)
					Assertions.assertTrue(char.posRef >= ref)
				}

				DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED -> {
					Assertions.assertTrue(char.posRef == ref || char.posRef == ref + 1)
					Assertions.assertTrue(char.posRaw >= raw)
				}

				DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED -> {
					Assertions.assertTrue(char.posRef == ref || char.posRef == ref + 1)
					Assertions.assertTrue(char.posRaw >= raw)
				}
			}

			raw = char.posRaw
			ref = char.posRef
		}

		when (mode) {
			DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED -> Assertions.assertTrue(raw == rawText.length - 1 || rawText.isEmpty() && raw == 0)
			DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED -> Assertions.assertTrue(ref == refText.length - 1 || refText.isEmpty() && ref == 0)
			DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED -> {}
		}

		Assertions.assertTrue(ref == refText.length - 1 || refText.isEmpty() && ref == 0)
	}
}