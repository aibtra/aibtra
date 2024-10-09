package dev.aibtra.diff

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiffExtenderTest {

	private var raw = ""
	private var diff = Diff.INITIAL
	private var extender = DiffExtender(false)

	@BeforeEach
	fun setup() {
		raw = ""
		diff = Diff.INITIAL
		extender = DiffExtender(false)
	}

	@Test
	fun testThreeChanges() {
		raw = "abcdefg"
		assert("", "", "")
		assert("a", " ", " ")
		assert("ax", "  ", "  ")
		assert("axc", " * ", " * ")
		assert("axcd", " *  ", " *  ")
		assert("axcdy", " *   ", " *   ")
		assert("axcdyf", " *  * ", " *  * ")
		assert("axcdyfz", " *  *  ", " *  *  ")
		assert("axcdyfz", " *  * *", " *  * *", true)
	}

	@Test
	fun testAdditionAtEnd() {
		raw = "a"
		assert("", "", "")
		assert("a", " ", " ")
		assert("ab", " ", "  ")
		assert("abc", " ", "   ")
		assert("abcd", " ", "    ")
		assert("abcde", " ", "     ")
		assert("abcde", " |", " ****", true)
	}

	@Test
	fun testRemovalAtEnd() {
		raw = "abcdefg"
		assert("", "", "")
		assert("a", " ", " ")
		assert("ab", "  ", "  ")
		assert("abc", "   ", "   ")
		assert("abc", "   ****", "   |", true)
	}

	@Test
	fun testAdditionAtStart() {
		raw = "a"
		assert("", "", "")
		assert("x", " ", " ")
		assert("xx", " ", "  ")
		assert("xxx", " ", "   ")
		assert("xxxa", "|", "*** ")
		assert("xxxa", "|", "*** ", true)
	}

	@Test
	fun testRemovalAtStart() {
		raw = "xxxa"
		assert("", "", "")
		assert("a", " ", " ")
		assert("a", "*** ", "|", true)
	}

	@Test
	fun testSteadyGrowth() {
		raw = "abcabcabc"
		assert("", "", "")
		assert("a", " ", " ")
		assert("aa", "  ", "  ")
		assert("aabc", " |  ", " *  ")
		assert("aabca", " |  ", " *   ")
		assert("aabcab", " |   ", " *    ")
		assert("aabcabbbb", " **     ", " |       ")
		assert("aabcabbbbc", " **     |", " |    *** ")
		assert("aabcabbbbcabc", " |   |   ", " *    ***    ")
		assert("aabcabbbbcabc", " |   |   ", " *    ***    ", true)
	}

	@Test
	fun testSteadyShrink() {
		raw = "abcabcabcabcabc"
		assert("", "", "")
		assert("a", " ", " ")
		assert("ab", "  ", "  ")
		assert("abab", "  * ", "  | ")
		assert("ababab", "  *  * ", "  | | ")
		assert("abababab", "  *  *  * ", "  | | | ")
		assert("ababababab", "  *  *  *  * ", "  | | | | ")
		assert("ababababab", "  *  *  *  *  *", "  | | | | |", true)
	}

	@Test
	fun testRefNonMonotone() {
		raw = "abc"
		assert("", "", "")
		assert("ab", "  ", "  ")
		assert("abc", "   ", "   ")
		assert("axc", " * ", " * ")
		assert("ay", "   ", "  ")
		assert("ayc", " * ", " * ")
		assert("azc", " * ", " * ")
		assert("abc", "   ", "   ")
		assert("abc", "   ", "   ", true)
	}

	private fun assert(ref: String, rawDiffExpected: String, refDiffExpected: String, finished: Boolean = false) {
		diff = extender.extend(raw, ref, diff, finished)

		val (rawDiffActual, refDiffActual) = DiffBuilderTest.formatBlocks(diff.raw.substring(0, diff.orgDiff.rawPredictionStart), diff.ref, diff.blocks)
		val failureMessage = "\"$ref\", \"$rawDiffActual\", \"$refDiffActual\"" + (if (finished) ", true" else "")
		Assertions.assertEquals(rawDiffExpected, rawDiffActual, failureMessage)
		Assertions.assertEquals(refDiffExpected, refDiffActual, failureMessage)
	}
}