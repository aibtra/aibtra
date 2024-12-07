package dev.aibtra.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlphaNumericTokenizingTest {

	@Test
	fun testEmpty() {
		assertTokens(
			"",
			emptyList(),
			emptyList()
		)
	}

	@Test
	fun testWithOnlyWhitespaces() {
		assertTokens(
			"    \t\t  \n\n  ",
			listOf("    \t\t  ", N, N, "  "),
			listOf(N, N)
		)
	}

	@Test
	fun testWhitespaceAtLineStartAndOrEnd() {
		assertTokens(
			"\tStart\n\t Both \t\nEnd\t \t",
			listOf("\t", "Start", N, "\t ", "Both", " \t", N, "End", "\t \t"),
			listOf("Start", N, "Both", N, "End")
		)
	}

	@Test
	fun testSimpleSentence() {
		assertTokens(
			"Hello, World!",
			listOf("Hello", ",", " ", "World", "!"),
			listOf("Hello", ",", W, "World", "!")
		)
	}

	@Test
	fun testConsecutiveWhitespaces() {
		assertTokens(
			"Word1    Word2\t\tWord3\n\nWord4\n\nWord5",
			listOf("Word1", "    ", "Word2", "\t\t", "Word3", N, N, "Word4", N, N, "Word5"),
			listOf("Word1", W, "Word2", W, "Word3", N, N, "Word4", N, N, "Word5")
		)
	}

	@Test
	fun testInputWithOnlyPunctuation() {
		assertTokens(
			"!!! ??? ,,, ;;;",
			listOf("!", "!", "!", " ", "?", "?", "?", " ", ",", ",", ",", " ", ";", ";", ";"),
			listOf("!", "!", "!", W, "?", "?", "?", " ", ",", ",", ",", " ", ";", ";", ";")
		)
	}

	@Test
	fun testComplexInput() {
		assertTokens(
			"Hello,   World!\nThis is a test.\nNew line here.\tTabbed.",
			listOf("Hello", ",", "   ", "World", "!", N, "This", " ", "is", " ", "a", " ", "test", ".", N, "New", " ", "line", " ", "here", ".", "\t", "Tabbed", "."),
			listOf("Hello", ",", W, "World", "!", N, "This", W, "is", W, "a", W, "test", ".", N, "New", W, "line", W, "here", ".", W, "Tabbed", ".")
		)
	}

	@Test
	fun testSingleCharacterTokens() {
		assertTokens(
			"A!\nB@C#D$",
			listOf("A", "!", N, "B", "@", "C", "#", "D", "$"),
			listOf("A", "!", N, "B", "@", "C", "#", "D", "$")
		)
	}

	private fun assertTokens(input: String, expectedTokensRaw: List<String>, expectedTokensCanonical: List<String>) {
		val tokenizing = AlphaNumericTokenizing.tokenize(input)
		assertEquals(expectedTokensRaw.size, tokenizing.length)
		assertEquals(expectedTokensRaw, tokenizing.tokens(false))
		assertEquals(expectedTokensCanonical, tokenizing.tokens(true))
	}

	companion object {
		const val W = AlphaNumericTokenizing.CANONICAL_WHITESPACE
		const val N = "\n"
	}
}
