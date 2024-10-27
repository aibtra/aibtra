package dev.aibtra.diff

import dev.aibtra.OptimisticLevenshteinDistance
import org.junit.Assert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OptimisticLevenshteinDistanceTest {

	@Test
	fun testEqualStrings() {
		assert("kitten", "kitten", 0, expectedSteps = 6)
	}

	@Test
	fun testEmptyStrings() {
		assert("", "", 0)
	}

	@Test
	fun testEmptyAndNonEmpty() {
		assert("", "kitten", 6)
	}

	@Test
	fun testSingleCharacterDifference() {
		assert("kitten", "sitten", 1)
	}

	@Test
	fun testInsertion() {
		assert("kitten", "kittten", 1)
	}

	@Test
	fun testDeletion() {
		assert("kitten", "kittn", 1)
	}

	@Test
	fun testSubstitution() {
		assert("kitten", "sittin", 2)
	}

	@Test
	fun testCompletelyDifferentStrings() {
		assert("kitten", "dog", 6)
	}

	@Test
	fun testUnicodeCharacters() {
		assert("こんにちは", "こんばんは", 2)
	}

	@Test
	fun testLongStrings() {
		val s1 = "a".repeat(1000)
		val s2 = "a".repeat(999) + "b"
		assert(s1, s2, 1)
	}

	@Test
	fun testAnagramABC() {
		assert("abc", "cba", 2)
	}

	@Test
	fun testAnagramGodDog() {
		assert("god", "dog", 2)
	}

	@Test
	fun testAnagramRatTar() {
		assert("rat", "tar", 2)
	}

	@Test
	fun testAnagramArcCar() {
		assert("arc", "car", 2)
	}

	@Test
	fun testAnagramActCat() {
		assert("act", "cat", 2)
	}

	@Test
	fun testAnagramEatTea() {
		assert("eat", "tea", 2)
	}

	@Test
	fun testAnagramEvilVile() {
		assert("evil", "vile", 2)
	}

	@Test
	fun testAnagramDustyStudy() {
		assert("dusty", "study", 4)
	}

	@Test
	fun testAnagramSaveVase() {
		assert("save", "vase", 2)
	}

	@Test
	fun testAnagramBragGrab() {
		assert("brag", "grab", 2)
	}

	@Test
	fun testAnagramFriedFired() {
		assert("fried", "fired", 2)
	}

	@Test
	fun testAnagramAngelGlean() {
		assert("angel", "glean", 5)
	}

	@Test
	fun testAnagramThingNight() {
		assert("thing", "night", 5)
	}

	@Test
	fun testAnagramBelowElbow() {
		assert("below", "elbow", 2)
	}

	@Test
	fun testAnagramListenSilent() {
		assert("listen", "silent", 4)
	}

	@Test
	fun testReversedStrings() {
		assert("stressed", "desserts", 6, expectedSteps = 74)
	}

	@Test
	fun testRandomStrings() {
		val random = Random(0) // Seeded for reproducibility
		val chars = ('a'..'m').toList()
		val numTests = 10000

		repeat(numTests) {
			val length1 = random.nextInt(0, 11)
			val length2 = random.nextInt(0, 11)
			val s1 = (1..length1).map { chars[random.nextInt(chars.size)] }.joinToString("")
			val s2 = (1..length2).map { chars[random.nextInt(chars.size)] }.joinToString("")

			assert(s1, s2, standardLevenshteinDistance(s1, s2))
		}
	}

	@Test
	fun testLimitNotReached1() {
		assert("kitten", "kitten", 0, maxEditDistance = 1, expectedSteps = 6)
	}

	@Test
	fun testLimitNotReached2() {
		assert("kitten", "kittten", 1, maxEditDistance = 1, expectedSteps = 9)
	}

	@Test
	fun testLimitReached() {
		assert("kitten", "sittten", -1, maxEditDistance = 1, expectedSteps = 6)
	}

	private fun assert(s1: String, s2: String, expected: Int, maxEditDistance: Int = Integer.MAX_VALUE, expectedSteps: Long? = null) {
		val distance = OptimisticLevenshteinDistance.compute(s1, s2) { _, _, editDistance -> editDistance > maxEditDistance }
		val actual = distance.distance()
		Assertions.assertEquals(expected, actual)
		expectedSteps?.let {
			Assertions.assertEquals(it, distance.steps())
		}
	}

	private fun standardLevenshteinDistance(s: String, t: String): Int {
		val m = s.length
		val n = t.length
		if (m == 0) {
			return n
		}
		if (n == 0) {
			return m
		}

		val dp = Array(m + 1) { IntArray(n + 1) }
		for (i in 0..m) {
			dp[i][0] = i
		}
		for (j in 0..n) {
			dp[0][j] = j
		}

		for (i in 1..m) {
			for (j in 1..n) {
				val cost = if (s[i - 1] == t[j - 1]) 0 else 1

				dp[i][j] = minOf(
					dp[i - 1][j] + 1,       // Deletion
					dp[i][j - 1] + 1,       // Insertion
					dp[i - 1][j - 1] + cost // Substitution
				)
			}
		}

		return dp[m][n]
	}
}
