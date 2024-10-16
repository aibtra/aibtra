package dev.aibtra.diff

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UnifiedDiffTest {
	@Test
	fun testSimpleLineModification() {
		assert(
			"""
				|Line 1
				|Line 2
				|Line 3
				|Line 4
				|Line 5
	    """,
			"""
				|@@ -3 +3 @@
				|-Line 3
				|+Modified Line 3
			""",
			"""
				|Line 1
				|Line 2
				|Modified Line 3
				|Line 4
				|Line 5
			"""
		)
	}

	@Test
	fun testLineDeletionAndAddition() {
		assert(
			"""
				|Apple
				|Banana
				|Cherry
				|Date
				|Elderberry
				|Fig
				|Grape
	    """,
			"""
				|@@ -3,2 +3,2 @@
				| Cherry
				|-Date
				|+Dragonfruit
				| Elderberry
			""",
			"""
				|Apple
				|Banana
				|Cherry
				|Dragonfruit
				|Elderberry
				|Fig
				|Grape
			"""
		)
	}

	@Test
	fun testMultipleHunksWithAdditionsAndDeletions() {
		assert(
			"""
				|First Line
				|Second Line
				|Third Line
				|Fourth Line
				|Fifth Line
				|Sixth Line
				|Seventh Line
				|Eighth Line
				|Ninth Line
				|Tenth Line
	    """,
			"""
				|@@ -2,3 +2,2 @@
				| Second Line
				|-Third Line
				|-Fourth Line
				|+Third Line Modified
				|@@ -5,2 +4,3 @@
				| Fifth Line
				|+Inserted Line
				| Sixth Line
				|@@ -8 +8 @@
				|-Eighth Line
				|+Eighth Line Modified
	    """,
			"""
				|First Line
				|Second Line
				|Third Line Modified
				|Fifth Line
				|Inserted Line
				|Sixth Line
				|Seventh Line
				|Eighth Line Modified
				|Ninth Line
				|Tenth Line
	    """
		)
	}

	@Test
	fun testAddingLinesAtBeginningAndEnd() {
		assert(
			"""
				|Existing Line 1
				|Existing Line 2
				|Existing Line 3
        """,
			"""
				|@@ -0,0 +1,2 @@
				|+Start Line 1
				|+Start Line 2
				|@@ -4,0 +6,2 @@
				|+End Line 1
				|+End Line 2
			""",
			"""
				|Start Line 1
				|Start Line 2
				|Existing Line 1
				|Existing Line 2
				|Existing Line 3
				|End Line 1
				|End Line 2
			"""
		)
	}

	@Test
	fun testDeletingMultipleLines() {
		assert(
			"""
        |Line 1
        |Line 2
        |Line 3
        |Line 4
        |Line 5
        |Line 6
        |Line 7
        |Line 8
        |Line 9
        |Line 10
      """,
			"""
        |@@ -3,5 +3,0 @@
        |-Line 3
        |-Line 4
        |-Line 5
        |-Line 6
        |-Line 7
			""",
			"""
        |Line 1
        |Line 2
        |Line 8
        |Line 9
        |Line 10
			"""
		)
	}

	@Test
	fun testReplacingEntireContent() {
		assert(
			"""
        |Old Content Line 1
        |Old Content Line 2
        |Old Content Line 3
        """,
			"""
        |@@ -1,3 +1,3 @@
        |-Old Content Line 1
        |-Old Content Line 2
        |-Old Content Line 3
        |+New Content Line 1
        |+New Content Line 2
        |+New Content Line 3
      """,
			"""
        |New Content Line 1
        |New Content Line 2
        |New Content Line 3
      """
		)
	}

	@Test
	fun testAddingAndDeletingLinesWithContext() {
		assert(
			"""
        |Context Line 1
        |Context Line 2
        |Context Line 3
        |Delete Line 1
        |Delete Line 2
        |Context Line 4
        |Context Line 5
			""",
			"""
        |@@ -3,4 +3,3 @@
        | Context Line 3
        |-Delete Line 1
        |-Delete Line 2
        |+Added Line 1
        | Context Line 4
			""",
			"""
        |Context Line 1
        |Context Line 2
        |Context Line 3
        |Added Line 1
        |Context Line 4
        |Context Line 5
			"""
		)
	}

	@Test
	fun testComplexChangesWithMultipleHunks() {
		assert(
			"""
        |Section A
        |Item 1
        |Item 2
        |Item 3
        |Section B
        |Item 4
        |Item 5
        |Item 6
        |Section C
        |Item 7
        |Item 8
        |Item 9
			""",
			"""
        |@@ -2,3 +2,4 @@
        | Item 1
        |+New Item 1.1
        | Item 2
        | Item 3
        |@@ -7,2 +7,0 @@
        | Item 5
        |-Item 6
        |@@ -9 +9,2 @@
        | Section C
        |+Inserted Item 6.1
			""",
			"""
        |Section A
        |Item 1
        |New Item 1.1
        |Item 2
        |Item 3
        |Section B
        |Item 4
        |Item 5
        |Section C
        |Inserted Item 6.1
        |Item 7
        |Item 8
        |Item 9
			"""
		)
	}

	private fun assert(base: String, diff: String, expected: String) {
		val unified = UnifiedDiff.parse(diff.trimMargin())
		val result = unified.applyHunks(base.trimMargin())
		assertEquals(expected.trimMargin(), result)
	}
}