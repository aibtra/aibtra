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
			"01234567890123456789",
			"01234567890123456789",
			"Everything is equal.",
			"                    ",
			"01234567890123456789",
			"01234567890123456789",
			"                    ",
			"01234567890123456789",
			"01234567890123456789"
		)
	}

	@Test
	fun testRefEmpty() {
		assert(
			"Some text yet to be processed",
			"-----------------------------",
			"01234567890123456789012345678",
			"-----------------------------",
			"",
			"-----------------------------",
			"01234567890123456789012345678",
			"-----------------------------",
			"",
			"",
			""
		)
	}

	@Test
	fun testInsertion() {
		assert(
			"Ther is a typo.",
			"   ><          ",
			"012345678901234",
			"012446789012345",
			"There is a typo.",
			"    +           ",
			"0123445678901234",
			"0123456789012345",
			"    +           ",
			"0123345678901234",
			"0123456789012345"
		)
	}

	@Test
	fun testInsertionAtStart() {
		assert(
			"There is a typo.",
			"<               ",
			"0123456789012345",
			"0234567890123456",
			"xThere is a typo.",
			"+                ",
			"00123456789012345",
			"01234567890123456",
			"+                ",
			"-0123456789012345",
			"01234567890123456"
		)
	}

	@Test
	fun testInsertionAtEnd() {
		assert(
			"There is a typo.",
			"               >",
			"0123456789012345",
			"0123456789012346",
			"There is a typo.x",
			"                +",
			"01234567890123456",
			"01234567890123456",
			"                +",
			"01234567890123455",
			"01234567890123456"
		)
	}

	@Test
	fun testRemoval() {
		assert(
			"Thereeee is a typo.",
			"     ---           ",
			"0123456789012345678",
			"0123444456789012345",
			"There is a typo.",
			"     ---           ",
			"0123456789012345678",
			"0123444456789012345",
			"    ><          ",
			"0123559012345678",
			"0123456789012345"
		)
	}

	@Test
	fun testRemovalAtStart() {
		assert(
			"xThere is a typo.",
			"-                ",
			"01234567890123456",
			"-0123456789012345",
			"There is a typo.",
			"-                ",
			"01234567890123456",
			"-0123456789012345",
			"<               ",
			"0234567890123456",
			"0123456789012345"
		)
	}

	@Test
	fun testRemovalAtEnd() {
		assert(
			"There is a typo.x",
			"                -",
			"01234567890123456",
			"01234567890123455",
			"There is a typo.",
			"                -",
			"01234567890123456",
			"01234567890123455",
			"               >",
			"0123456789012346",
			"0123456789012345"
		)
	}

	@Test
	fun testRemovalInProgress1() {
		assert(
			"Theree is a typo.",
			"     ~~~         ",
			"01234567890123456",
			"01234555777777777",
			"There is",
			"     ---+++",
			"01234567888",
			"01234555567",
			"     ~~~",
			"01234555",
			"01234567",
			rawTo = 8
		)
	}

	@Test
	fun testRemovalInProgress2() {
		assert(
			"Theree is a typo.",
			"     -           ",
			"01234567890123456",
			"01234456777777777",
			"There is",
			"     -   ",
			"012345678",
			"012344567",
			"    ><  ",
			"01235578",
			"01234567",
			rawTo = 9
		)
	}

	@Test
	fun testRemovalInProgress3() {
		assert(
			"Theree is a typo.",
			"     -           ",
			"01234567890123456",
			"01234456788888888",
			"There is ",
			"     -    ",
			"0123456789",
			"0123445678",
			"    ><   ",
			"012355789",
			"012345678",
			rawTo = 10
		)
	}

	private fun assert(raw: String, rawCharsExpected: String, rawCharsRawPossExpected: String, rawCharsRefPossExpected: String, ref: String, refFullCharsExpected: String, refFullCharsRawPossExpected: String, refFullCharsRefPossExpected: String, refKeptCharsExpected: String, refKeptCharsRawPossExpected: String, refKeptCharsRefPossExpected: String, rawTo: Int = raw.length) {
		val blocks = DiffBuilder(raw.substring(0, rawTo), ref, true, true, true).build()
		val diff = Diff(raw, rawTo, ref, blocks, false)
		val rawFormatted = DiffFormatter(DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED).format(diff)
		val rawCharsActual = formatChars(raw, ref, rawFormatted.second, DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED)
		val rawCharsRawPossActual = formatRawCharPoss(raw, ref, rawFormatted.second, DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED)
		val rawCharsRefPossActual = formatRefCharPoss(raw, ref, rawFormatted.second, DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED)
		val refFullFormatted = DiffFormatter(DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED).format(diff)
		val refFullCharsActual = formatChars(raw, ref, refFullFormatted.second, DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED)
		val refFullCharsRawPossActual = formatRawCharPoss(raw, ref, refFullFormatted.second, DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED)
		val refFullCharsRefPossActual = formatRefCharPoss(raw, ref, refFullFormatted.second, DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED)
		val refKeptFormatted = DiffFormatter(DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED).format(diff)
		val refKeptCharsActual = formatChars(raw, ref, refKeptFormatted.second, DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED)
		val refKeptCharsRawPossActual = formatRawCharPoss(raw, ref, refKeptFormatted.second, DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED)
		val refKeptCharsRefPossActual = formatRefCharPoss(raw, ref, refKeptFormatted.second, DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED)

		Assertions.assertEquals(
			format(raw, rawCharsExpected, rawCharsRawPossExpected, rawCharsRefPossExpected, ref, refFullCharsExpected, refFullCharsRawPossExpected, refFullCharsRefPossExpected, refKeptCharsExpected, refKeptCharsRawPossExpected, refKeptCharsRefPossExpected),
			format(raw, rawCharsActual, rawCharsRawPossActual, rawCharsRefPossActual, ref, refFullCharsActual, refFullCharsRawPossActual, refFullCharsRefPossActual, refKeptCharsActual, refKeptCharsRawPossActual, refKeptCharsRefPossActual)
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

	private fun formatRawCharPoss(rawText: String, refText: String, chars: List<DiffChar>, mode: DiffFormatter.Mode): String {
		assertChars(rawText, refText, chars, mode)

		val text = StringBuilder()
		for (char in chars) {
			text.append(if (char.posRaw >= 0) (char.posRaw % 10 + '0'.code).toChar() else '-')
		}

		return text.toString()
	}

	private fun formatRefCharPoss(rawText: String, refText: String, chars: List<DiffChar>, mode: DiffFormatter.Mode): String {
		assertChars(rawText, refText, chars, mode)

		val text = StringBuilder()
		for (char in chars) {
			text.append(if (char.posRef >= 0) (char.posRef % 10 + '0'.code).toChar() else '-')
		}

		return text.toString()
	}

	private fun format(rawText: String, rawChars: String, rawCharsRawPoss: String, rawCharsRefPoss: String, refFullText: String, refFullChars: String, refFullCharsRawPoss: String, refFullCharsRefPoss: String, refKeptChars: String, refKeptCharsRawPoss: String, refKeptCharsRefPoss: String): String {
		val text = StringBuilder()
		text.append("\"$rawText\",\n")
		text.append("\"$rawChars\",\n")
		text.append("\"$rawCharsRawPoss\",\n")
		text.append("\"$rawCharsRefPoss\",\n")
		text.append("\"$refFullText\",\n")
		text.append("\"$refFullChars\",\n")
		text.append("\"$refFullCharsRawPoss\",\n")
		text.append("\"$refFullCharsRefPoss\",\n")
		text.append("\"$refKeptChars\",\n")
		text.append("\"$refKeptCharsRawPoss\",\n")
		text.append("\"$refKeptCharsRefPoss\"\n")
		return text.toString()
	}

	private fun assertChars(rawText: String, refText: String, chars: List<DiffChar>, mode: DiffFormatter.Mode) {
		when (mode) {
			DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED -> Assertions.assertEquals(rawText.length, chars.size)
			DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED -> Assertions.assertEquals(refText.length, chars.size)
			DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED -> {}
		}
	}
}