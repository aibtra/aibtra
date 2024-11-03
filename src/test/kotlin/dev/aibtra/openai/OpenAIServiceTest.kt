package dev.aibtra.openai

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class OpenAIServiceTest {

	@Test
	fun testExactObject() {
		assert(
			"a\nb\nc\nfoooo\nx\ny\nz",
			"""
				{
					"old": "foooo"
					"new": "foo"
					"oldLineStart": 3
				}
			""".trimIndent(),
			0,
			"a\nb\nc\nfoo\nx\ny\nz"
		)
	}

	@Test
	fun testExactObjectMultipleTimes1() {
		assert(
			"a\nfoooo\nc\nfoooo\nx\nfoooo\nz",
			"""
				{
					"old": "foooo"
					"new": "foo"
					"oldLineStart": 3
				}
			""".trimIndent(),
			0,
			"a\nfoo\nc\nfoooo\nx\nfoooo\nz"
		)
	}

	@Test
	fun testExactObjectMultipleTimes2() {
		assert(
			"a\nfoooo\nc\nfoooo\nx\nfoooo\nz",
			"""
				{
					"old": "foooo"
					"new": "foo"
					"oldLineStart": 3
				}
			""".trimIndent(),
			10,
			"a\nfoooo\nc\nfoo\nx\nfoooo\nz"
		)
	}

	@Test
	fun testExactObjectMultipleTimes3() {
		assert(
			"a\nfoooo\nc\nfoooo\nx\nfoooo\nz",
			"""
				{
					"old": "foooo"
					"new": "foo"
					"oldLineStart": 3
				}
			""".trimIndent(),
			17,
			"a\nfoooo\nc\nfoooo\nx\nfoo\nz"
		)
	}

	@Test
	fun testFuzzyObject() {
		assert(
			"a\nb\nc\nfoooooooooooooooooooooooooooooooooooooooo\nx\ny\nz",
			"""
				{
					"old": "xoooooooooooooooooooooooooooooooooooooooo"
					"new": "foo"
				}
			""".trimIndent(),
			0,
			"a\nb\nc\nfoo\nx\ny\nz"
		)
	}

	@Test
	fun testFuzzyObjectAtStart() {
		assert(
			"foooooooooooooooooooooooooooooooooooooooo\nx\ny\nz",
			"""
				{
					"old": "xoooooooooooooooooooooooooooooooooooooooo"
					"new": "foo"
				}
			""".trimIndent(),
			0,
			"foo\nx\ny\nz"
		)
	}

	@Test
	fun testFuzzyObjectAtEnd() {
		assert(
			"a\nb\nc\nfoooooooooooooooooooooooooooooooooooooooo",
			"""
				{
					"old": "xoooooooooooooooooooooooooooooooooooooooo"
					"new": "foo"
				}
			""".trimIndent(),
			0,
			"a\nb\nc\nfoo"
		)
	}

	@Test
	fun testTwoFuzzyObjects() {
		assert(
			"a\nfoooooooooooooooooooooooooooooooooooooooo\nc\nbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaar\nd",
			"""
				[
					{
						"old": "xoooooooooooooooooooooooooooooooooooooooo"
						"new": "foo"
					},
					{
						"old": "xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaar"
						"new": "bar"
					}
				]
			""".trimIndent(),
			0,
			"a\nfoo\nc\nbar\nd"
		)
	}

	@Test
	fun testTwoFuzzyObjectsWithMultipleCandidates1() {
		// 2024-11-03: This happened to me with deploy.yml when I was attempting to modify the "release" job, using selection mode.
		// I received an array of two objects, and the first one described a single line change to "env", "TAG", which
		// was mistakenly applied to the "build" job because the line was identical.
		assert(
			"u\nfoooooooooooooooooooooooooooooooooooooooo\nv\nw\nx\ny\nz\na\nfoooooooooooooooooooooooooooooooooooooooo\nc\nbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaar\nd",
			"""
				[
					{
						"old": "xoooooooooooooooooooooooooooooooooooooooo"
						"new": "foo"
					},
					{
						"old": "xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaar"
						"new": "bar"
					}
				]
			""".trimIndent(),
			54,
			"u\nfoooooooooooooooooooooooooooooooooooooooo\nv\nw\nx\ny\nz\na\nfoo\nc\nbar\nd"
		)
	}

	@Test
	fun testTwoFuzzyObjectsWithMultipleCandidates2() {
		// 2024-11-03: This happened to me with deploy.yml when I was attempting to modify the "release" job, using selection mode.
		// I received an array of two objects, and the first one described a single line change to "env", "TAG", which
		// was mistakenly applied to the "build" job because the line was identical.
		assert(
			"u\nfoooooooooooooooooooooooooooooooooooooooo\nv\nw\nx\ny\nz\na\nfoooooooooooooooooooooooooooooooooooooooo\nc\nbaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaar\nd",
			"""
				{
					"changes:" [
						{
							"old": "xoooooooooooooooooooooooooooooooooooooooo"
							"new": "foo"
						},
						{
							"old": "xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaar"
							"new": "bar"
						}
					]
				}
			""".trimIndent(),
			54,
			"u\nfoooooooooooooooooooooooooooooooooooooooo\nv\nw\nx\ny\nz\na\nfoo\nc\nbar\nd"
		)
	}

	@Test
	fun testJsonMarkdownWithExplanations() {
		assert(
			"a\nb\nc\nfooo\nd\ne\nf",
			"""
				```json
				{
					"old": "fooo"
					"new": "foo"
				}
				```
				
				# Some explanations
				
				Whatever
			""".trimIndent(),
			0,
			"a\nb\nc\nfoo\nd\ne\nf"
		)
	}

	@Test
	fun testInvalidOldLineStart() {
		assert(
			"a\nb\nc\nfooo\nd\ne\nf",
			"""
				{
					"old": "fooo"
          "oldLineStart":  **[Specify the exact line number where this line starts]**,
					"new": "foo"
				}
			""".trimIndent(),
			0,
			"a\nb\nc\nfoo\nd\ne\nf"
		)
	}

	private fun assert(content: String, json: String, focusStart: Int, expected: String) {
		val actual = OpenAIService.applyJson(json, content, focusStart)
		Assertions.assertEquals(expected, actual)
	}
}
