package dev.aibtra.core

import org.junit.jupiter.api.*

class StringUtilsTest {

	@Test
	fun testLineEndingsNone() {
		assertLineEnding(StringUtils.Eol.UNIX, "")
	}

	@Test
	fun testLineEndingsUnix1() {
		assertLineEnding(StringUtils.Eol.UNIX, "\n")
	}

	@Test
	fun testLineEndingsUnix2() {
		assertLineEnding(StringUtils.Eol.UNIX, "a\n")
	}

	@Test
	fun testLineEndingsUnix3() {
		assertLineEnding(StringUtils.Eol.UNIX, "a\nb")
	}

	@Test
	fun testLineEndingsUnix4() {
		assertLineEnding(StringUtils.Eol.UNIX, "a\nb\n")
	}

	@Test
	fun testLineEndingsUnix5() {
		assertLineEnding(StringUtils.Eol.UNIX, "\n\n")
	}

	@Test
	fun testLineEndingsUnix6() {
		assertLineEnding(StringUtils.Eol.UNIX, "a\nb\nc\n")
	}

	@Test
	fun testLineEndingsUnix7() {
		assertLineEnding(StringUtils.Eol.UNIX, "\n\n\n")
	}

	@Test
	fun testLineEndingsMacos1() {
		assertLineEnding(StringUtils.Eol.MACOS, "\r")
	}

	@Test
	fun testLineEndingsMacos2() {
		assertLineEnding(StringUtils.Eol.MACOS, "a\r")
	}

	@Test
	fun testLineEndingsMacos3() {
		assertLineEnding(StringUtils.Eol.MACOS, "a\rb")
	}

	@Test
	fun testLineEndingsMacos4() {
		assertLineEnding(StringUtils.Eol.MACOS, "a\rb\r")
	}

	@Test
	fun testLineEndingsMacos5() {
		assertLineEnding(StringUtils.Eol.MACOS, "\r\r")
	}

	@Test
	fun testLineEndingsMacos6() {
		assertLineEnding(StringUtils.Eol.MACOS, "a\rb\rc\r")
	}

	@Test
	fun testLineEndingsMacos7() {
		assertLineEnding(StringUtils.Eol.MACOS, "\r\r\r")
	}

	@Test
	fun testLineEndingsWindows1() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "\r\n")
	}

	@Test
	fun testLineEndingsWindows2() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "a\r\n")
	}

	@Test
	fun testLineEndingsWindows3() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "a\r\nb")
	}

	@Test
	fun testLineEndingsWindows4() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "a\r\nb\r\n")
	}

	@Test
	fun testLineEndingsWindows5() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "\r\n\r\n")
	}

	@Test
	fun testLineEndingsWindows6() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "a\r\nb\r\nc\r\n")
	}

	@Test
	fun testLineEndingsWindows7() {
		assertLineEnding(StringUtils.Eol.WINDOWS, "\r\n\r\n\r\n")
	}

	@Test
	fun testEndingsInvalid1() {
		assertLineEnding(null, "\n\r")
	}

	@Test
	fun testEndingsInvalid2() {
		assertLineEnding(null, "a\nb\r")
	}

	@Test
	fun testEndingsInvalid3() {
		assertLineEnding(null, "a\nb\r\n")
	}

	@Test
	fun testEndingsInvalid4() {
		assertLineEnding(null, "a\rb\n")
	}

	@Test
	fun testEndingsInvalid5() {
		assertLineEnding(null, "a\r\nb\n")
	}

	@Test
	fun testFixIndentationNone() {
		assertFixIndentation(
			"""
				|1
				|2
				|3
			""".trimMargin(),
			"""
				|A
				|B
				|C
			""".trimMargin(),
			"""
				|A
				|B
				|C
			""".trimMargin()
		)
	}

	@Test
	fun testFixIndentationSpaces() {
		assertFixIndentation(
			"""
				| 1
				|  2
				|   3
			""".trimMargin(),
			"""
				|A
				|  B
				| C
			""".trimMargin(),
			"""
				| A
				|   B
				|  C
			""".trimMargin()
		)
	}

	@Test
	fun testFixIndentationTabs() {
		assertFixIndentation(
			"""
				| 1
				|   2
				|     3
			""".trimMargin(),
			"""
				|   A
				|  B
				| C
			""".trimMargin(),
			"""
				|   A
				|  B
				| C
			""".trimMargin()
		)
	}

	@Test
	fun testFixIndentationMixed() {
		assertFixIndentation(
			"""
				|         1
				|         2
				|         3
			""".trimMargin(),
			"""
				|A
				| B
				| C
			""".trimMargin(),
			"""
				|         A
				|          B
				|          C
			""".trimMargin()
		)
	}

	private fun assertLineEnding(expected: StringUtils.Eol?, text: String) {
		Assertions.assertEquals(expected, StringUtils.determineLineEnding(text))
	}

	private fun assertFixIndentation(template: String, input: String, expected: String) {
		val actual = StringUtils.fixIndentation(input, template)
		Assertions.assertEquals(expected, actual)
	}
}