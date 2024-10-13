package dev.aibtra.main.frame

import dev.aibtra.main.frame.WorkFile.Eol
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class WorkFileDetermineLineEndingTest {

	@Test
	fun testLineEndingNone() {
		assertLineEnding(Eol.UNIX, "")
	}

	@Test
	fun testLineEndingUnix1() {
		assertLineEnding(Eol.UNIX, "\n")
	}

	@Test
	fun testLineEndingUnix2() {
		assertLineEnding(Eol.UNIX, "a\n")
	}

	@Test
	fun testLineEndingUnix3() {
		assertLineEnding(Eol.UNIX, "a\nb")
	}

	@Test
	fun testLineEndingUnix4() {
		assertLineEnding(Eol.UNIX, "a\nb\n")
	}

	@Test
	fun testLineEndingUnix5() {
		assertLineEnding(Eol.UNIX, "\n\n")
	}

	@Test
	fun testLineEndingUnix6() {
		assertLineEnding(Eol.UNIX, "a\nb\nc\n")
	}

	@Test
	fun testLineEndingUnix7() {
		assertLineEnding(Eol.UNIX, "\n\n\n")
	}

	@Test
	fun testLineEndingMacos1() {
		assertLineEnding(Eol.MACOS, "\r")
	}

	@Test
	fun testLineEndingMacos2() {
		assertLineEnding(Eol.MACOS, "a\r")
	}

	@Test
	fun testLineEndingMacos3() {
		assertLineEnding(Eol.MACOS, "a\rb")
	}

	@Test
	fun testLineEndingMacos4() {
		assertLineEnding(Eol.MACOS, "a\rb\r")
	}

	@Test
	fun testLineEndingMacos5() {
		assertLineEnding(Eol.MACOS, "\r\r")
	}

	@Test
	fun testLineEndingMacos6() {
		assertLineEnding(Eol.MACOS, "a\rb\rc\r")
	}

	@Test
	fun testLineEndingMacos7() {
		assertLineEnding(Eol.MACOS, "\r\r\r")
	}

	@Test
	fun testLineEndingWindows1() {
		assertLineEnding(Eol.WINDOWS, "\r\n")
	}

	@Test
	fun testLineEndingWindows2() {
		assertLineEnding(Eol.WINDOWS, "a\r\n")
	}

	@Test
	fun testLineEndingWindows3() {
		assertLineEnding(Eol.WINDOWS, "a\r\nb")
	}

	@Test
	fun testLineEndingWindows4() {
		assertLineEnding(Eol.WINDOWS, "a\r\nb\r\n")
	}

	@Test
	fun testLineEndingWindows5() {
		assertLineEnding(Eol.WINDOWS, "\r\n\r\n")
	}

	@Test
	fun testLineEndingWindows6() {
		assertLineEnding(Eol.WINDOWS, "a\r\nb\r\nc\r\n")
	}

	@Test
	fun testLineEndingWindows7() {
		assertLineEnding(Eol.WINDOWS, "\r\n\r\n\r\n")
	}

	@Test
	fun testInvalid1() {
		assertLineEnding(null, "\n\r")
	}

	@Test
	fun testInvalid2() {
		assertLineEnding(null, "a\nb\r")
	}

	@Test
	fun testInvalid3() {
		assertLineEnding(null, "a\nb\r\n")
	}

	@Test
	fun testInvalid4() {
		assertLineEnding(null, "a\rb\n")
	}

	@Test
	fun testInvalid5() {
		assertLineEnding(null, "a\r\nb\n")
	}

	private fun assertLineEnding(expected: Eol?, text: String) {
		Assertions.assertEquals(expected, WorkFile.determineLineEnding(text))
	}
}