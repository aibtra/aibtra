package dev.aibtra.resolver

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class ResolverLinesTest {

	@Test
	fun testEmpty() {
		val lines = ResolverLines("")
		assertLines(lines, "")
		assertOffsets(lines, 0)
	}

	@Test
	fun testEmptyWithLF() {
		val lines = ResolverLines("\n")
		assertLines(lines, "", "")
		assertIndexes(lines, 0)
		assertOffsets(lines, 0, 1)
	}

	@Test
	fun testEmptyWithLFLF() {
		val lines = ResolverLines("\n\n")
		assertLines(lines, "", "", "")
		assertIndexes(lines, 0, 1)
		assertOffsets(lines, 0, 1, 2)
	}

	@Test
	fun testSingleLine() {
		val lines = ResolverLines("1")
		assertLines(lines, "1")
		assertIndexes(lines, 0)
		assertOffsets(lines, 0)
	}

	@Test
	fun testSingleLineWithLF() {
		val lines = ResolverLines("1\n")
		assertLines(lines, "1", "")
		assertIndexes(lines, 0, 0)
		assertOffsets(lines, 0, 2)
	}

	@Test
	fun testTwoLines() {
		val lines = ResolverLines("abc\nd")
		assertLines(lines, "abc", "d")
		assertIndexes(lines, 0, 0, 0, 0, 1)
		assertOffsets(lines, 0, 4)
	}

	@Test
	fun testTwoLinesWithLF() {
		val lines = ResolverLines("abc\nd\n")
		assertLines(lines, "abc", "d", "")
		assertIndexes(lines, 0, 0, 0, 0, 1, 1)
		assertOffsets(lines, 0, 4, 6)
	}

	private fun assertLines(lines: ResolverLines, vararg strings: String) {
		assertEquals(strings.size, lines.size)
		assertEquals(strings.toList(), lines.strings)
	}

	private fun assertIndexes(lines: ResolverLines, vararg indexes: Int) {
		for (pos in indexes.indices) {
			assertEquals(indexes[pos], lines.index(pos))
		}
	}

	private fun assertOffsets(lines: ResolverLines, vararg offsets: Int) {
		for (index in offsets.indices) {
			assertEquals(offsets[index], lines.offset(index))
		}
	}

	private fun assertSubstring(lines: ResolverLines, expected: String, lineFrom: Int, lineTo: Int) {
		assertEquals(expected, lines.subString(lineFrom, lineTo))
	}
}