package dev.aibtra.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LineTokenizingTest {

	@Test
	fun testEmptyInput() {
		assertLines(
			"",
			listOf(
				listOf()
			)
		)
	}

	@Test
	fun testSingleLineWithoutNewline() {
		assertLines(
			"This is a single line.",
			listOf(
				listOf("This", " ", "is", " ", "a", " ", "single", " ", "line", ".")
			)
		)
	}

	@Test
	fun testMultipleLinesWithNewlines() {
		assertLines(
			"First line.\nSecond line!\nThird line?",
			listOf(
				listOf("First", " ", "line", ".", "\n"),
				listOf("Second", " ", "line", "!", "\n"),
				listOf("Third", " ", "line", "?")
			)
		)
	}

	@Test
	fun testMultipleConsecutiveNewlines() {
		assertLines(
			"Line one.\n\n\nLine four.",
			listOf(
				listOf("Line", " ", "one", ".", "\n"),
				listOf("\n"),
				listOf("\n"),
				listOf("Line", " ", "four", ".")
			)
		)
	}

	@Test
	fun testTrailingNewline() {
		assertLines(
			"Line one.\nLine two.\n",
			listOf(
				listOf("Line", " ", "one", ".", "\n"),
				listOf("Line", " ", "two", ".", "\n"),
				listOf()
			)
		)
	}

	@Test
	fun testInputWithOnlyNewlines() {
		assertLines(
			"\n\n\n",
			listOf(
				listOf("\n"),
				listOf("\n"),
				listOf("\n"),
				listOf()
			)
		)
	}

	@Test
	fun testInputWithVariousWhitespaceCharacters() {
		assertLines(
			"Word1\tWord2 \tWord3 \t Word4\nWord5",
			listOf(
				listOf(					"Word1", "\t", "Word2", " \t", "Word3", " \t ", "Word4", "\n"				),
				listOf("Word5")
			)
		)
	}

	@Test
	fun testInputWithOnlyWordCharacters() {
		assertLines(
			"Word1Word2Word3",
			listOf(
				listOf("Word1Word2Word3")
			)
		)
	}

	@Test
	fun testInputWithMixedWordAndNonWordCharacters() {
		assertLines(
			"Hello, World!\nKotlin_123\tTest.",
			listOf(
				listOf("Hello", ",", " ", "World", "!", "\n"),
				listOf("Kotlin_123", "\t", "Test", ".")
			)
		)
	}

	@Test
	fun testInputWithMultipleTypesOfTokens() {
		assertLines(
			"var1 = 100;\nvar2 = var1 + 200;\nprint(var2);",
			listOf(
				listOf("var1", " ", "=", " ", "100", ";", "\n"),
				listOf("var2", " ", "=", " ", "var1", " ", "+", " ", "200", ";", "\n"),
				listOf("print", "(", "var2", ")", ";")
			)
		)
	}

	@Test
	fun testInputWithUnicodeCharacters() {
		assertLines(
			"こんにちは\n世界!",
			listOf(
				listOf("こんにちは", "\n"),
				listOf("世界", "!")
			)
		)
	}

	@Test
	fun testGetAllLineTokensReturnsCorrectMapping() {
		assertLines(
			"Alpha\nBeta Gamma\nDelta",
			listOf(
				listOf("Alpha", "\n"),
				listOf("Beta", " ", "Gamma", "\n"),
				listOf("Delta")
			)
		)
	}

	@Test
	fun testGetAllLineTokensAsListReturnsCorrectList() {
		assertLines(
			"One\nTwo Three\nFour Five Six",
			listOf(
				listOf("One", "\n"),
				listOf("Two", " ", "Three", "\n"),
				listOf("Four", " ", "Five", " ", "Six")
			)
		)
	}

	private fun assertLines(input: String, expected: List<List<String>>) {
		val tokenizing = LineTokenizing.tokenize(input)
		assertEquals(expected.size, tokenizing.size)
		for ((index, expectedTokens) in expected.withIndex()) {
			assertEquals(expectedTokens, tokenizing.tokens(index, false, true))
		}
		assertEquals(input, tokenizing.string())
	}
}
