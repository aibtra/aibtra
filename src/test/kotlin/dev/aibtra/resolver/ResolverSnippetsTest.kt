package dev.aibtra.resolver

import dev.aibtra.core.*
import org.junit.jupiter.api.*
import java.nio.file.*
import java.nio.file.attribute.*

class ResolverSnippetsTest {

	@Test
	fun testNoConflict() {
		reduce(
			"a.z.c.",
			"a.b.c.",
			"a.x.c.",
			"a.y.c.",
			0,
			listOf()
		)
	}

	@Test
	fun testPureConflict() {
		reduce(
			"a.${conflict("x", "y")}.c.",
			"a.b.c.",
			"a.x.c.",
			"a.y.c.",
			0,
			Conflict("b", "x", "y")
		)
	}

	@Test
	fun testBadConflict() {
		reduce(
			"a.${conflict("x", "z")}.c.",
			"a.b.c.",
			"a.x.c.",
			"a.y.c.",
			0,
			Conflict("b", "x", "y")
		)
	}

	@Test
	fun testOursEmptyConflict() {
		reduce(
			"a.${conflict("", "y")}.c.",
			"a.b.c.",
			"a.x.c.",
			"a.y.c.",
			0,
			Conflict("b", "x", "y")
		)
	}

	@Test
	fun testTheirsEmptyConflict() {
		reduce(
			"a.${conflict("", "y")}.c.",
			"a.b.c.",
			"a.c.",
			"a.y.c.",
			0,
			Conflict("b", "c", "y")
		)
	}

	@Test
	fun testSingleConflictOursAdditionBefore() {
		reduce(
			"1.2.a.${conflict("x", "y")}.c.",
			"a.b.c.",
			"1.2.a.x.c.",
			"a.y.c.",
			0,
			Conflict("b", "x", "y")
		)
	}

	@Test
	fun testSingleConflictTheirsAdditionBefore() {
		reduce(
			"1.2.3.a.${conflict("x", "y")}.c.",
			"a.b.c.",
			"a.x.c.",
			"1.2.3.a.y.c.",
			0,
			Conflict("b", "x", "y")
		)
	}

	@Test
	fun testSingleConflictTheirsAndOursAdditionBefore() {
		reduce(
			"1.2.3.a.${conflict("x", "y")}.c.",
			"a.b.c.",
			"1.2.3.a.x.c.",
			"1.2.3.a.y.c.",
			0,
			Conflict("b", "x", "y")
		)
	}

	@Test
	fun testConflictWithEmptyLinesInAndBefore() {
		reduce(
			".${conflict("m..n..o", "u..v..w..x")}.",
			".a..b..c.",
			".m..n..o.",
			".u..v..w.x",
			0,
			Conflict("a..b..c", "m..n..o", "u..v..w.x")
		)
	}

	@Test
	fun testConflictWithEmptyLinesInAndAfter() {
		reduce(
			"${conflict("m..n..o", "u..v..w..x")}..",
			"a..b..c..",
			"m..n..o..",
			"u..v..w.x.",
			0,
			listOf(Conflict("a..b..c", "m..n..o", "u..v..w.x"))
		)
	}

	@Test
	fun testConflictWithEmptyLinesInAndBeforeAndAfter() {
		reduce(
			".${conflict("m..n..o", "u..v..w")}.p.x.",
			".a..b..c.d.x.",
			".m..n..o.x.",
			".u..v..w.p.x.",
			0,
			listOf(Conflict("a..b..c.d", "m..n..o", "u..v..w.p"))
		)
	}

	@Test
	fun testThreePureConflicts() {
		reduce(
			"a.${conflict("x", "y")}.c.${conflict("x", "y")}.e.${conflict("x", "y")}.g",
			"a.b.c.d.e.f.g",
			"a.x.c.x.e.x.g",
			"a.y.c.y.e.y.g",
			0,
			listOf(Conflict("b", "x", "y"), Conflict("d", "x", "y"), Conflict("f", "x", "y"))
		)
	}

	@Test
	fun testThreePureConflictsWithMoreDistance() {
		reduce(
			"a.a.a.${conflict("x", "y")}.c.c.c.${conflict("x", "y")}.e.e.e.${conflict("x", "y")}.g.g.g",
			"a.a.a.b.c.c.c.d.e.e.e.f.g",
			"a.a.a.x.c.c.c.x.e.e.e.x.g",
			"a.a.a.y.c.c.c.y.e.e.e.y.g",
			0,
			listOf(Conflict("b", "x", "y"), Conflict("d", "x", "y"), Conflict("f", "x", "y"))
		)
	}

	@Test
	fun testThreePureConflictsWithAllOtherLinesEqual() {
		reduce(
			"a.a.a.${conflict("x", "y")}.a.a.a.${conflict("x", "y")}.a.a.a.${conflict("x", "y")}.a.a.a",
			"a.a.a.b.a.a.a.d.a.a.a.f.g",
			"a.a.a.x.a.a.a.x.a.a.a.x.g",
			"a.a.a.y.a.a.a.y.a.a.a.y.g",
			0,
			listOf(Conflict("b", "x", "y"), Conflict("d", "x", "y"), Conflict("f", "x", "y"))
		)
	}

	@Test
	fun testOursManyInsertions() {
		reduce(
			"a.a.a.1.${conflict("x", "y")}.3.",
			"1.2.3.",
			"a.a.a.1.x.3.",
			"1.y.3.",
			0,
			listOf(Conflict("2", "x", "y"))
		)
	}

	@Test
	fun testOursReducedNotOverlappingOursBase() {
		reduce(
			".${conflict("2..1..3", "13..14..15..16")}.4.",
			".1..2..3.4.",
			"5.6.7.8.9.10..2..1..3.4.",
			"11.6.7.12.9.10..13..14..15..16.4.",
			0,
			listOf(Conflict("1..2..3", "5.6.7.8.9.10..2..1.", "13..14..15..16"))
		)
	}

	@Test
	fun testAdjacentConflicts() {
		reduce(
			"a.${conflict("x", "y")}.${conflict("x", "y")}.${conflict("x", "y")}.e",
			"a.b.c.d.e",
			"a.x.x.x.e",
			"a.y.y.y.e",
			0,
			listOf(Conflict("b.c.d", "x.x.x", "y.y.y"), Conflict("b.c.d", "x.x.x", "y.y.y"), Conflict("b.c.d", "x.x.x", "y.y.y"))
		)
	}

	@Test
	fun testContext1() {
		reduce(
			"a.${conflict("x", "y")}.c.",
			"a.b.c.",
			"a.x.c.",
			"a.y.c.",
			1,
			Conflict("a.b.c", "a.x.c", "a.y.c")
		)
	}

	@Test
	fun testContext3DropNewLines() {
		reduce(
			"...a.${conflict("x", "y")}.c....",
			"...a.b.c....",
			"...a.x.c....",
			"...a.y.c....",
			3,
			Conflict("a.b.c", "a.x.c", "a.y.c")
		)
	}

	companion object {
		fun conflict(ours: String, theirs: String): String {
			return "<<<<<<< HEAD${if (ours.isNotEmpty()) "." + ours else ""}.=======.${if (theirs.isNotEmpty()) theirs + "." else ""}>>>>>>> theirs"
		}
	}

	private fun reduce(draftContent: String, baseContent: String, oursContent: String, theirsContents: String, context: Int, expected: Conflict) {
		reduce(draftContent, baseContent, oursContent, theirsContents, context, listOf(expected))
	}

	private fun reduce(draftContent: String, baseContent: String, oursContent: String, theirsContents: String, context: Int, expected: List<Conflict>) {
		val files = ResolverFiles(
			Path.of(""),
			listOf(
				ResolverFile(
					"draft", FileTime.fromMillis(0),
					"draft", draftContent.replace(".", "\n"), StringUtils.Eol.UNIX,
					baseContent.replace(".", "\n"),
					oursContent.replace(".", "\n"),
					theirsContents.replace(".", "\n")
				)
			),
			overviewFile = Path.of("")
		)

		val snippets = ResolverSnippets.compute(files, context)
		val actual = snippets.map { Conflict(it.base.join(false).replace("\n", "."), it.ours.join(false).replace("\n", "."), it.theirs.join(false).replace("\n", ".")) }
		Assertions.assertEquals(expected, actual)
	}

	private data class Conflict(val base: String, val ours: String, val theirs: String)
}