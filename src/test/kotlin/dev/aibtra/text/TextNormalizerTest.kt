/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.text

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TextNormalizerTest {

	@Test
	fun testPreserveLeadingAndTrailingNewLines1() {
		assert(
			"\nThis is a test.\n",
			"\nThis is a test.\n",
		)
	}

	@Test
	fun testPreserveLeadingAndTrailingNewLines2() {
		assert(
			"\n\n\n\nThis is a test.\n\n\n",
			"\n\n\n\nThis is a test.\n\n\n",
		)
	}

	@Test
	fun testJoinLinesPlainTextSkip1() {
		assert(
			"This is\na test",
			"This is a test",
		)
	}

	@Test
	fun testJoinLinesPlainTextSkip2() {
		assert(
			"This is\n a test",
			"This is a test",
		)
	}

	@Test
	fun testJoinLinesPlainTextSkip3() {
		assert(
			"This is \n a \n  test",
			"This is a test",
		)
	}

	@Test
	fun testJoinLinesPlainTextPreserve1() {
		assert(
			"This is\n\na test",
			"This is\n\na test",
		)
	}

	@Test
	fun testJoinLinesPlainTextPreserve2() {
		assert(
			"This is \n \n a test",
			"This is\n\na test",
		)
	}

	@Test
	fun testJoinLinesBlockQuote1() {
		assert(
			"This\n\n>is\n>a\n\n test",
			"This\n\n>is\n>a\n\ntest",
		)
	}

	@Test
	fun testJoinLinesBlockQuote2() {
		assert(
			"This\n>is\n>a\n test",
			"This\n>is\n>a\n test",
		)
	}

	@Test
	fun testJoinLinesCodeBlock1() {
		assert(
			"This is \n\n```\ncode\ncode\ncode\n```\n\n a test",
			"This is\n\n```\ncode\ncode\ncode\n```\n\na test",
		)
	}

	@Test
	fun testJoinLinesCodeBlock2() {
		assert(
			"This is \n```\ncode\ncode\ncode\n```\n a test",
			"This is\n```\ncode\ncode\ncode\n```\na test",
		)
	}

	@Test
	fun testJoinLinesHeadings() {
		assert(
			"# foo\n## bar\n### baz",
			"# foo\n## bar\n### baz",
		)
	}

	@Test
	fun testJoinLinesEnumeration1() {
		assert(
			"- foo\n  - bar\n   - baz",
			"- foo\n  - bar\n   - baz",
		)
	}

	@Test
	fun testJoinLinesEnumeration2() {
		assert(
			"+ foo\n  + bar\n   + baz",
			"+ foo\n  + bar\n   + baz",
		)
	}

	@Test
	fun testJoinLinesEnumeration3() {
		assert(
			"  * foo\n* bar\n    * baz",
			"  * foo\n* bar\n    * baz",
		)
	}

	@Test
	fun testJoinLinesEnumeration4() {
		assert(
			"  1. foo\n1. bar\n    1. baz",
			"  1. foo\n1. bar\n    1. baz",
		)
	}

	@Test
	fun testJoinLinesEnumeration5() {
		assert(
			"  1. foo\n2. bar\n    3. baz",
			"  1. foo\n2. bar\n    3. baz",
		)
	}

	@Test
	fun testNoJoinLines() {
		assert(
			"This is\na test",
			"This is\na test",
			joinLines = false
		)
	}

	@Test
	fun testChangeDoubleToSingleBlockQuotes1() {
		assert(
			"Some\n> single\n> quotes",
			"Some\n> single\n> quotes",
		)
	}

	@Test
	fun testChangeDoubleToSingleBlockQuotes2() {
		assert(
			"Some\n>> double\n>> quotes\n> and\n\nmore text",
			"Some\n> double\n> quotes\n> and\n\nmore text",
		)
	}

	@Test
	fun testChangeDoubleToSingleBlockQuotes3() {
		assert(
			"Some\n>>> triple\n>>> triple\n\nand more text",
			"Some\n>>> triple\n>>> triple\n\nand more text",
		)
	}

	@Test
	fun testNoChangeDoubleToSingleBlockQuotes() {
		assert(
			"Some\n>> double\n>> quotes\n> and\n\nmore text",
			"Some\n>> double\n>> quotes\n> and\n\nmore text",
			changeDoubleToSingleBlockQuotes = false
		)
	}

	@Test
	fun testChangeDoubleToSingleBlockQuotesFixMissingLines() {
		assert(
			"Some\n>> double\n>> quotes\n>> and\nmore text",
			"Some\n> double\n> quotes\n> and\n\nmore text",
		)
	}

	@Test
	fun testRewrapBlockQuotes1() {
		assert(
			"> some quoted and more quoted . . and and and quoted super-super-super-long text",
			"> some\n> quoted and\n> more\n> quoted . .\n> and and\n> and quoted\n> super-super-super-long\n> text",
			rewrapBlockQuotesAt = 10
		)
	}

	@Test
	fun testRewrapBlockQuotes2() {
		assert(
			"> 123 456 789 012 345\n>     678 901 234 567 890   123   456    789\n> 012    345 678 901 234",
			"> 123 456\n> 789 012\n> 345\n> 678 901\n> 234 567\n> 890   123\n> 456\n> 789 012\n> 345 678\n> 901 234",
			rewrapBlockQuotesAt = 10
		)
	}

	@Test
	fun testRewrapBlockQuotes3() {
		assert(
			"Quote:\n> 12345 658 9 0123 456\n\nend of quote, no more newlines",
			"Quote:\n> 12345\n> 658 9\n> 0123 456\n\nend of quote, no more newlines",
			rewrapBlockQuotesAt = 10
		)
	}

	@Test
	fun testRewrapBlockQuotes4() {
		assert(
			"Quote\n> 123456789012345\n\nend",
			"Quote\n> 123456789012345\n\nend",
			rewrapBlockQuotesAt = 10
		)
	}

	@Test
	fun testRewrapBlockQuotes5() {
		assert(
			"Quote\n> 123 456 789\n\nend",
			"Quote\n> 123 456\n> 789\n\nend",
			rewrapBlockQuotesAt = 10
		)
	}

	@Test
	fun testRewrapBlockQuotes6() {
		assert(
			"Quote\n\n> 123 456 789\n\nend",
			"Quote\n\n> 123 456\n> 789\n\nend",
			rewrapBlockQuotesAt = 10
		)
	}

	@Test
	fun testRewrapBlockQuotes7() {
		assert(
			"> quote\n\n",
			"> quote\n\n",
			rewrapBlockQuotesAt = 10
		)
	}

	private fun assert(text: String, expected: String, fixMissingEmptyLineAfterBlockQuote: Boolean = true, joinLines: Boolean = true, changeDoubleToSingleBlockQuotes: Boolean = true, rewrapBlockQuotesAt: Int = 0) {
		val normalized = TextNormalizer(TextNormalizer.Config(joinLines, fixMissingEmptyLineAfterBlockQuote, changeDoubleToSingleBlockQuotes, rewrapBlockQuotesAt != 0, rewrapBlockQuotesAt)).normalize(text)
		Assertions.assertEquals(expected, normalized)
	}
}