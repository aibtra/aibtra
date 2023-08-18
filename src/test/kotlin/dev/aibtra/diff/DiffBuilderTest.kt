/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DiffBuilderTest {

	@Test
	fun testSingleTypo() {
		assert(
			"Ther is a typo.",
			"    |          ",
			"    |          ",
			"There is a typo.",
			"    *           ",
			"    *           ",
			"Ther* is a typo.",
			"There is a typo.",
			"Ther* is a typo.",
			"There is a typo."
		)
	}

	@Test
	fun testShiftToWordBoundaries1() {
		assert(
			"fxo foo",
			"  ||   ",
			"       |",
			"fxo foo foo",
			"  *** *    ",
			"       ****",
			"fx***o* foo",
			"fxo foo foo",
			"fxo foo****",
			"fxo foo foo"
		)
	}

	@Test
	fun testShiftToWordBoundaries2() {
		assert(
			"fxo foo fxo",
			"  ||       ",
			"       |   ",
			"fxo foo foo fxo",
			"  *** *        ",
			"       ****    ",
			"fx***o* foo fxo",
			"fxo foo foo fxo",
			"fxo foo**** fxo",
			"fxo foo foo fxo"
		)
	}

	@Test
	fun testShiftToWordBoundaries3() {
		assert(
			"invoking from terminal",
			"        |      ||     ",
			"        |    |        ",
			"invoking it from the terminal",
			"        ***       * ***      ",
			"        ***     ****         ",
			"invoking*** from t*e***rminal",
			"invoking it from the terminal",
			"invoking*** from**** terminal",
			"invoking it from the terminal"
		)
	}

	@Test
	fun testShiftToWordBoundaries4() {
		assert(
			"'status' to be always present",
			"         * ***        |      ",
			"         *****       |       ",
			"'status' would always be present",
			"         * ***        ***       ",
			"         *****       ***        ",
			"'status' t*o be*** always ***present",
			"'status' *wo***uld always be present",
			"'status' to be***** always*** present",
			"'status' *****would always be present"
		)
	}

	@Test
	fun testJoinCloseBlockInSameWord1() {
		assert(
			"flockinaucinihixlipyliication",
			"    *          *   *  |      ",
			"    *          *******       ",
			"floccinaucinihilipilification",
			"    *          |  *  *       ",
			"    *          *******       ",
			"flock*inaucinihixlipy*li*ication",
			"floc*cinaucinihi*lip*ilification",
			"flock*inaucinihixlipyli*******ication",
			"floc*cinaucinihi*******lipilification"
		)
	}

	@Test
	fun testJoinCloseBlockInSameWord2() {
		assert(
			"a flockinaucinihixlipyliication",
			"**    *          *   *  |      ",
			"**    *          *******       ",
			"floccinaucinihilipilification",
			"|   *          |  *  *       ",
			"|   *          *******       ",
			"a flock*inaucinihixlipy*li*ication",
			"**floc*cinaucinihi*lip*ilification",
			"a flock*inaucinihixlipyli*******ication",
			"**floc*cinaucinihi*******lipilification"
		)
	}

	@Test
	fun testJoinCloseBlockInSameWord3() {
		assert(
			"flockinaucinihixlipyliication",
			"|   *          *   *  |      ",
			"|   *          *******       ",
			"a floccinaucinihilipilification",
			"**    *          |  *  *       ",
			"**    *          *******       ",
			"**flock*inaucinihixlipy*li*ication",
			"a floc*cinaucinihi*lip*ilification",
			"**flock*inaucinihixlipyli*******ication",
			"a floc*cinaucinihi*******lipilification"
		)
	}

	@Test
	fun testAdjustCommonStart1a() {
		assert(
			"to get the problem reproducible",
			"  **** ** *   ** * * |  ****   |",
			"   ***************** **********",
			"to reproduce the problem",
			"  |* |  *** |** *   |  *",
			"   ************ ********",
			"to get th*e probl***em r**e*producible*",
			"to**** **re*pro**duce* *the pro****blem",
			"to get the problem r************eproducible********",
			"to *****************reproduce the********** problem"
		)
	}

	@Test
	fun testAdjustCommonStart1b() {
		assert(
			" to get the problem reproducible",
			"*  **** ** *   ** * * |  ****   |",
			"********************* **********",
			"to reproduce the problem",
			"| |* |  *** |** *   |  *",
			"*************** ********",
			" to get th*e probl***em r**e*producible*",
			"*to**** **re*pro**duce* *the pro****blem",
			" to get the problem r***************eproducible********",
			"*********************to reproduce the********** problem"
		)
	}

	@Test
	fun testAdjustCommonStart1c() {
		assert(
			"  to get the problem reproducible",
			"**   ****** *   ** * * |  ****   |",
			"********************** **********",
			"to reproduce the problem",
			"|  * |  *** |** *   |  *",
			"*************** ********",
			"  to get th*e probl***em r**e*producible*",
			"**to ******re*pro**duce* *the pro****blem",
			"  to get the problem r***************eproducible********",
			"**********************to reproduce the********** problem"
		)
	}

	private fun assert(raw: String, rawBlocksCoreExpected: String, rawBlocksAdjustedExpected: String, ref: String, refBlocksCoreExcepted: String, refBlocksAdjustedExpected: String, rawAlignedCoreExpected: String, refAlignedCoreExpected: String, rawAlignedAdjustedExcepted: String, refAlignedAdjustedExcepted: String) {
		val blocksCore = DiffBuilder(raw, ref, false, false, false).build()
		val rawBlocksCoreActual = StringBuilder(" ".repeat(raw.length))
		val refBlocksCoreActual = StringBuilder(" ".repeat(ref.length))
		formatBlocks(blocksCore, rawBlocksCoreActual, refBlocksCoreActual)

		val blocksAdjusted = DiffBuilder(raw, ref, true, true, true).build()
		val rawBlocksAdjustedActual = StringBuilder(" ".repeat(raw.length))
		val refBlocksAdjustedActual = StringBuilder(" ".repeat(ref.length))
		formatBlocks(blocksAdjusted, rawBlocksAdjustedActual, refBlocksAdjustedActual)

		val rawAlignedCoreActual = StringBuilder()
		val refAlignedCoreActual = StringBuilder()
		alignBlocks(blocksCore, raw, ref, rawAlignedCoreActual, refAlignedCoreActual)

		val rawAlignedAdjustedActual = StringBuilder()
		val refAlignedAdjustedActual = StringBuilder()
		alignBlocks(blocksAdjusted, raw, ref, rawAlignedAdjustedActual, refAlignedAdjustedActual)

		Assertions.assertEquals(
			format(raw, rawBlocksCoreExpected, rawBlocksAdjustedExpected, ref, refBlocksCoreExcepted, refBlocksAdjustedExpected, rawAlignedCoreExpected, refAlignedCoreExpected, rawAlignedAdjustedExcepted, refAlignedAdjustedExcepted),
			format(raw, rawBlocksCoreActual.toString(), rawBlocksAdjustedActual.toString(), ref, refBlocksCoreActual.toString(), refBlocksAdjustedActual.toString(), rawAlignedCoreActual.toString(), refAlignedCoreActual.toString(), rawAlignedAdjustedActual.toString(), refAlignedAdjustedActual.toString())
		)
	}

	private fun formatBlocks(blocks: List<DiffBlock>, formattedRaw: StringBuilder, formattedRef: StringBuilder) {
		for (block in blocks) {
			if (block.rawFrom < block.rawTo) {
				for (i in block.rawFrom until block.rawTo) {
					formattedRaw[i] = '*'
				}
			}
			else if (block.rawTo < formattedRaw.length) {
				formattedRaw[block.rawTo] = '|'
			}
			else {
				formattedRaw.append("|")
			}

			if (block.refFrom < block.refTo) {
				for (i in block.refFrom until block.refTo) {
					formattedRef[i] = '*'
				}
			}
			else if (block.refTo < formattedRef.length) {
				formattedRef[block.refTo] = '|'
			}
			else {
				formattedRef.append("|")
			}
		}
	}

	private fun alignBlocks(blocks: List<DiffBlock>, raw: String, ref: String, formattedRaw: StringBuilder, formattedRef: StringBuilder) {
		var rawLast = 0
		var refLast = 0
		for (block in blocks) {
			formattedRaw.append(raw.substring(rawLast, block.rawFrom))
			formattedRaw.append(raw.substring(block.rawFrom, block.rawTo))
			formattedRaw.append("*".repeat(block.refTo - block.refFrom))

			formattedRef.append(ref.substring(refLast, block.refFrom))
			formattedRef.append("*".repeat(block.rawTo - block.rawFrom))
			formattedRef.append(ref.substring(block.refFrom, block.refTo))

			rawLast = block.rawTo
			refLast = block.refTo
		}

		formattedRaw.append(raw.substring(rawLast, raw.length))
		formattedRef.append(ref.substring(refLast, ref.length))
	}

	private fun format(rawText: String, rawBlocksCore: String, rawBlocksAdjusted: String, refText: String, refBlocksCore: String, refBlocksAdjusted: String, rawAlignedCore: String, refAlignedCore: String, rawAlignedAdjusted: String, refAlignedAdjusted: String): String {
		val text = StringBuilder()
		text.append("\"$rawText\",\n")
		text.append("\"$rawBlocksCore\",\n")
		text.append("\"$rawBlocksAdjusted\",\n")
		text.append("\"$refText\",\n")
		text.append("\"$refBlocksCore\",\n")
		text.append("\"$refBlocksAdjusted\",\n")
		text.append("\"$rawAlignedCore\",\n")
		text.append("\"$refAlignedCore\",\n")
		text.append("\"$rawAlignedAdjusted\",\n")
		text.append("\"$refAlignedAdjusted\"\n")
		return text.toString()
	}
}