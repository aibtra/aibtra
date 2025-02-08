package dev.aibtra.resolver

import dev.aibtra.core.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.file.attribute.*

class ResolverPatcherTest {

	@Test
	fun testFixIdentation1() {
		val snippet = createSnippet(
			"\t{",
			"\t\tx", "\t\ty",
			"\t}"
		)

		assertResolution(
			"z",
			"\t{",
			"\t\tz",
			"\t}",
			snippet
		)
	}

	@Test
	fun testFixIdentation2() {
		val snippet = createSnippet(
			"\t{",
			"\t \t  x", "\t \t     y",
			"\t}."
		)

		assertResolution(
			"z",
			"\t{",
			"\t \t  z",
			"\t}..",
			snippet
		)
	}

	@Test
	fun testFixIdentation3() {
		val snippet = createSnippet(
			"{",
			"\tx.\tx..\tx", "\ty.\ty..\ty",
			"}"
		)

		assertResolution(
			"z.z..z.",
			"{",
			"\tz.\tz..\tz",
			"}",
			snippet
		)
	}

	@Test
	fun testFixIdentation4() {
		val snippet = createSnippet(
			"  {",
			"    x", "    y",
			"  }"
		)

		assertResolution(
			"\t\tz",
			"  {",
			"    z",
			"  }",
			snippet
		)
	}

	@Test
	fun testJustConflict1() {
		val snippet = createSnippet(
			"a.b.c",
			"x", "y",
			"d.e.f"
		)

		assertResolution(
			"x.y",
			"a.b.c",
			"x.y",
			"d.e.f",
			snippet
		)
	}

	@Test
	fun testJustConflict2() {
		val snippet = createSnippet(
			"a.b",
			"x", "y",
			"d.e"
		)

		assertResolution(
			"x.y",
			"a.b",
			"x.y",
			"d.e",
			snippet
		)
	}

	@Test
	fun testJustConflictIndentation() {
		val snippet = createSnippet(
			"a",
			"\t\tx", "\t\ty",
			"d"
		)

		assertResolution(
			"x.y",
			"a",
			"\t\tx.\t\ty",
			"d",
			snippet
		)
	}

	@Test
	fun testJustConflictWithSingleOverlappingLineBefore() {
		val snippet = createSnippet(
			"\ta.b.c.d.e.{",
			"\t\tx", "\t\ty",
			"\tf.g.h.i.j"
		)

		assertResolution(
			"{.x.y.",
			"\ta.b.c.d.e.{",
			"\t\tx.\t\ty",
			"\tf.g.h.i.j",
			snippet
		)
	}

	@Test
	fun testJustConflictWithSingleOverlappingLineAfter() {
		val snippet = createSnippet(
			"\ta.b.c.d.e",
			"\t\tx", "\t\ty",
			"\t}.f.g.h.i.j"
		)

		assertResolution(
			"x.y.}.",
			"\ta.b.c.d.e",
			"\t\tx.\t\ty",
			"\t}.f.g.h.i.j",
			snippet
		)
	}

	@Test
	fun testJustConflictWithThreeOverlappingLinesBefore() {
		val snippet = createSnippet(
			"\ta.b.c.d.e.{._.{",
			"\t\tx", "\t\ty",
			"\tf.g.h.i.j"
		)

		assertResolution(
			"{._.{.x.y.",
			"\ta.b.c.d.e.{._.{",
			"\t\tx.\t\ty",
			"\tf.g.h.i.j",
			snippet
		)
	}

	@Test
	fun testJustConflictWithThreeOverlappingLinesAfter() {
		val snippet = createSnippet(
			"\ta.b.c.d.e",
			"\t\tx", "\t\ty",
			"\t}._.}.f.g.h.i.j"
		)

		assertResolution(
			"x.y.}._.}.",
			"\ta.b.c.d.e",
			"\t\tx.\t\ty",
			"\t}._.}.f.g.h.i.j",
			snippet
		)
	}

	@Test
	fun testCanonicalWhitespaces() {
		val snippet = createSnippet(
			"\ta  a \t a",
			"\tx", "\ty",
			"\tb  b \t b"
		)

		assertResolution(
			"x.y",
			"\ta  a \t a",
			"\tx.\ty",
			"\tb  b \t b",
			snippet
		)
	}

	private fun assertResolution(res: String, expectedBefore: String, expectedConflict: String, expectedAfter: String, snippet: ResolverSnippet) {
		val resolution = ResolverPatcher.apply(res.replace(".", "\n"), snippet)
		assertEquals(expectedBefore.replace(".", "\n").let { it + if (it.isNotEmpty() && !it.endsWith("\n")) "\n" else "" }, resolution.content.before)
		assertEquals(expectedConflict.replace(".", "\n") + "\n", resolution.content.text)
		assertEquals(expectedAfter.replace(".", "\n").let { it + if (it.isNotEmpty() && !it.endsWith("\n")) "\n" else "" }, resolution.content.after)
	}

	private fun createSnippet(before: String, ours: String, theirs: String, after: String): ResolverSnippet {
		val conflictText = ResolverSnippetsTest.conflict(ours, theirs).replace(".", "\n")
		val conflicts = ResolverConflict.extract(conflictText)
		require(conflicts.size == 1)

		val draftContent = ResolverSnippet.Content("${before.replace(".", "\n")}\n", "$conflictText\n", "${after.replace(".", "\n")}\n")
		val baseContent = ResolverSnippet.Content("", "", "")
		val oursContent = ResolverSnippet.Content("", "", "")
		val theirsContent = ResolverSnippet.Content("", "", "")
		val draftText = draftContent.join(true)
		val resolverFile = ResolverFile("file", FileTime.fromMillis(0), "file", draftText, StringUtils.Eol.UNIX, "", "", "")
		val draftPortion = ResolverDraftPortion(resolverFile, 0, draftText.length)
		return ResolverSnippet(ResolverId("0"), resolverFile, conflicts[0], draftContent, baseContent, oursContent, theirsContent, draftPortion, null)
	}
}