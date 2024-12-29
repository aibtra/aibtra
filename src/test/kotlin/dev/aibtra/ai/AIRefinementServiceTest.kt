package dev.aibtra.ai

import dev.aibtra.ai.AIResolverService.*
import dev.aibtra.resolver.*
import org.junit.jupiter.api.*

class AIRefinementServiceTest {

	@Test
	fun testAssertResolutionsExpectedFormat() {
		// Expected format
		assertResolutions(
			"""
					```
					CONFLICT-ID: a
					FILENAME: A
					RESOLUTION:
					a
					```

					```
					CONFLICT-ID: b
					FILENAME: B
					RESOLUTION:
					b
					```
					
					```
					CONFLICT-ID: c
					FILENAME: C
					RESOLUTION:
					c
					```
				""".trimIndent(),
			Resolution(ResolverId("a"), "a", "a"),
			Resolution(ResolverId("b"), "b", "b"),
			Resolution(ResolverId("c"), "c", "c")
		)
	}

	@Test
	fun testAssertResolutionsFenced1() {
		// Seen on 2024-12-04
		assertResolutions(
			"""
					```
					CONFLICT-ID: a
					FILENAME: A
					RESOLUTION:
					```
					a
					```
					
					```
					CONFLICT-ID: b
					FILENAME: B
					RESOLUTION:
					```
					b
					```
					
					```
					CONFLICT-ID: c
					FILENAME: C
					RESOLUTION:
					```
					c
					```
				""".trimIndent(),
			Resolution(ResolverId("a"), "a", "a"),
			Resolution(ResolverId("b"), "b", "b"),
			Resolution(ResolverId("c"), "c", "c")
		)
	}

	@Test
	fun testAssertResolutionsFenced2() {
		// Seen on 2024-12-05
		assertResolutions(
			"""
					```
					CONFLICT-ID: a
					FILENAME: A
					RESOLUTION:
					```
					```kotlin
					a
					```
					```
					CONFLICT-ID: b
					FILENAME: B
					RESOLUTION:
					```
					```kotlin
					b
					```
					```
					CONFLICT-ID: c
					FILENAME: C
					RESOLUTION:
					```
					```kotlin
					c
					```
				""".trimIndent(),
			Resolution(ResolverId("a"), "a", "a"),
			Resolution(ResolverId("b"), "b", "b"),
			Resolution(ResolverId("c"), "c", "c")
		)
	}

	private fun assertResolutions(input: String, vararg expectedResolutions: Resolution) {
		val expected = expectedResolutions.asList().map { Pair(it.id, it.resolution) }
		val actual = AIResolverService.extractResolutions(input, mutableMapOf()).map { Pair(it.id, it.resolution) }
		Assertions.assertEquals(expected, actual)
	}
}
