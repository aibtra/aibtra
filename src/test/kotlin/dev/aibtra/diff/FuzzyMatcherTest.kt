package dev.aibtra.diff

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.StringBuilder
import kotlin.random.Random

class FuzzyMatcherTest {

	private var errorThreshold: Int = 0
	private var errorRatio: Int = Integer.MAX_VALUE
	private var startPos: Int = 0
	private var lineMode: Boolean = false

	@BeforeEach
	fun setup() {
		errorThreshold = 0
		errorRatio = Integer.MAX_VALUE
		startPos = 0
		lineMode = false
	}

	@Test
	fun testPerfectMatch() {
		assertMatch("foo\nbar", "foo", 0)
		assertMatch("foo\nbar", "bar", 4)

		startPos = 1
		assertMatch("foo\nbar", "foo", -1)
	}

	@Test
	fun testSingleErrorMatch() {
		errorThreshold = 1
		assertMatch("foo\nbar", "baz", 4)
	}

	@Test
	fun testTwoErrorsNoMatch() {
		errorThreshold = 1
		assertMatch("foo\nbar", "az", -1)
	}

	@Test
	fun testGoodErrorRatioMatch() {
		errorRatio = 5
		assertMatch("foo\nbarbaz", "baraz", 4)
	}

	@Test
	fun testBadErrorRatioNoMatch() {
		errorRatio = 6
		assertMatch("foo\nbarbaz", "baraz", -1)
	}

	@Test
	fun testEmptyStrings() {
		// Matching empty needle in empty haystack
		assertMatch("", "", 0)
		// Matching empty needle in non-empty haystack
		assertMatch("foo", "", -1)
		// Matching non-empty needle in empty haystack
		assertMatch("", "foo", -1)
	}

	@Test
	fun testNeedleLongerThanHaystack() {
		// Needle is longer than haystack
		assertMatch("foo", "foobar", -1)
	}

	@Test
	fun testStartPositionOutOfBounds() {
		// Start position is beyond the haystack length
		startPos = 10
		assertMatch("foo\nbar", "bar", -1)
	}

	@Test
	fun testUnicodeCharacters() {
		// Matching with Unicode characters
		assertMatch("föö\nbär", "bär", 4)
		// Matching with slight differences
		errorThreshold = 1
		assertMatch("föö\nbär", "bår", 4) // 'ä' vs 'å' differs by one character
	}

	@Test
	fun testNeedleWithNewlines() {
		// Needle contains multiple lines
		assertMatch("foo\nbar\nbaz", "bar\nbaz", 4)
	}

	@Test
	fun testNeedleWithMoreLinesThanHaystack() {
		// Needle has more lines than haystack
		assertMatch("foo\nbar", "foo\nbar\nbaz", -1)
	}

	@Test
	fun testErrorThresholdBoundary() {
		// Distance is exactly at errorThreshold
		errorThreshold = 1
		assertMatch("foo\nbar", "far", 4) // 'bar' vs 'far' distance = 1
		// Distance exceeds errorThreshold
		assertMatch("foo\nbar", "faz", -1) // 'bar' vs 'faz' distance = 2
	}

	@Test
	fun testErrorRatioBoundary() {
		// distance * errorRatio == needleString.length
		errorRatio = 2
		assertMatch("foo\nbarbaz", "barz", 4) // 'barbaz' vs 'barz', distance = 2
		// distance * errorRatio > needleString.length
		assertMatch("foo\nbarbaz", "baz", -1) // 'barbaz' vs 'baz', distance = 3
	}

	@Test
	fun testRepeatedPatterns() {
		// Haystack with repeated patterns
		val haystack = "foo\nbar\nfoo\nbar\nfoo\nbar"
		assertMatch(haystack, "bar", 4) // Should match the first 'bar' at position 4
		startPos = 7
		assertMatch(haystack, "bar", 12) // Should match 'bar' at position 12
		startPos = 15
		assertMatch(haystack, "bar", 20) // Should match 'bar' at position 20
	}

	@Test
	fun testMultiplePossibleMatches() {
		// Multiple possible matches in haystack
		errorThreshold = 1
		val haystack = "foo\nbaz\nfoo\nbar"
		assertMatch(haystack, "baz", 4) // Should match 'baz' at position 4
		assertMatch(haystack, "bar", 12) // Should match 'bar' at position 12
	}

	@Test
	fun testPartialMatchNotFound() {
		// Partial match should not be found
		assertMatch("foo\nbar", "ba", -1) // 'ba' is a substring but not a full line
	}

	@Test
	fun testRandomLinesLarge1() {
		testRandomLinesLarge(0, 93397)
	}

	@Test
	fun testRandomLinesLarge2() {
		testRandomLinesLarge(1, 93031)
	}

	@Test
	fun testRandomLinesLarge3() {
		testRandomLinesLarge(2, 91494)
	}

	private fun testRandomLinesLarge(seed: Int, expectedSteps: Long) {
		errorThreshold = 1
		errorRatio = 100

		val random = Random(seed) // Seeded for reproducibility
		val chars = ('a'..'m').toList()
		val textLineCount = 10000
		val blockLineCount = 1000
		val mutationCount = blockLineCount / 200

		val lines = mutableListOf<String>()
		repeat(textLineCount) {
			val lineLength = random.nextInt(0, 123)
			lines.add((1..lineLength).map { chars[random.nextInt(chars.size)] }.joinToString(""))
		}

		val text = lines.joinToString("\n")
		val blockStart = random.nextInt(textLineCount - blockLineCount)
		val blockLines = lines.subList(blockStart, blockStart + blockLineCount)
		val blockRaw = blockLines.joinToString("\n")
		val blockPos = text.indexOf(blockRaw)
		require(blockPos >= 0)

		val blockBuilder = StringBuilder(blockRaw)
		repeat(mutationCount) {
			when (random.nextInt(3)) {
				0 -> blockBuilder.setCharAt(random.nextInt(blockBuilder.length), chars[random.nextInt(chars.size)])
				1 -> {
					val pos = random.nextInt(blockBuilder.length - 1)
					blockBuilder.removeRange(pos, pos + 1)
				}
				2 -> {
					blockBuilder.insert(random.nextInt(blockBuilder.length), chars[random.nextInt(chars.size)])
				}
			}
		}

		val block = blockBuilder.toString()
		require(!text.contains(block))

		assertMatch(text, block, blockPos, expectedSteps)
	}

	private fun assertMatch(haystack: String, needle: String, expectedPos: Int, expectedSteps : Long? = null) {
		val matcher = FuzzyMatcher.findBestMatch(haystack, needle, startPos, errorThreshold, errorRatio)
		val actualPos = matcher.bestStart
		Assertions.assertEquals(expectedPos, actualPos)
		expectedSteps?.let {
			Assertions.assertEquals(it, matcher.steps)
		}
	}
}
