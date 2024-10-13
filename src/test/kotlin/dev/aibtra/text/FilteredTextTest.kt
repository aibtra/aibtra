/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.text

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FilteredTextTest {

	@Test
	fun testEmpty() {
		assert(
			"",
			"",
		)
	}

	@Test
	fun testAsIs() {
		assert(
			"Nothing to filter.",
			"Nothing to filter.",
		)
	}

	@Test
	fun testAsIsPart1() {
		assert(
			FilteredText.Part("Nothing to filter.", 4, 7),
			"Nothing to filter.",
			"ing",
		)
	}

	@Test
	fun testAsIsPart2() {
		assert(
			FilteredText.Part("Nothing to filter.", 3, 7),
			"Nothing to filter.",
			"hing",
		)
	}

	@Test
	fun testAsIsPart3() {
		assert(
			FilteredText.Part("Nothing to filter.", 5, 7),
			"Nothing to filter.",
			"ng",
		)
	}

	@Test
	fun testInlineCodeSimple() {
		assert(
			"A `bcd` e",
			"A `<56886785befe0b2aecf3f4098a90e2501110e916>` e",
		)
	}

	@Test
	fun testInlineCodeSimplePartContaining() {
		assert(
			FilteredText.Part("A `bcd` e", 2, 7),
			"A `<56886785befe0b2aecf3f4098a90e2501110e916>` e",
			"`<56886785befe0b2aecf3f4098a90e2501110e916>`",
		)
	}

	@Test
	fun testInlineCodeSimplePartIntersectingLeft() {
		assert(
			FilteredText.Part("A `bcd` e", 3, 7),
			"A `<56886785befe0b2aecf3f4098a90e2501110e916>` e",
			"`<56886785befe0b2aecf3f4098a90e2501110e916>`",
		)
	}

	@Test
	fun testInlineCodeSimplePartIntersectingRight() {
		assert(
			FilteredText.Part("A `bcd` e", 2, 6),
			"A `<56886785befe0b2aecf3f4098a90e2501110e916>` e",
			"`<56886785befe0b2aecf3f4098a90e2501110e916>`",
		)
	}

	@Test
	fun testInlineCodeSimplePartIntersectingBoth() {
		assert(
			FilteredText.Part("A `bcd` e", 3, 6),
			"A `<56886785befe0b2aecf3f4098a90e2501110e916>` e",
			"`<56886785befe0b2aecf3f4098a90e2501110e916>`",
		)
	}

	@Test
	fun testInlineCodeComplex() {
		assert(
			"Some `code` here and some `code` there and almost `everywhere`",
			"Some `<4f5a32b86618fd9d6a870ffe890cf77a88669783>` here and some `<4f5a32b86618fd9d6a870ffe890cf77a88669783>` there and almost `<99153e20141def65836b1f52cdb7fafd3e989b4c>`",
		)
	}

	@Test
	fun testInlineCodeInEnumeration() {
		assert(
			"- Some `code`",
			"- Some `<4f5a32b86618fd9d6a870ffe890cf77a88669783>`",
		)
	}

	@Test
	fun testCodeBlock1() {
		assert(
			"""
				|```
				|a
				|code
				|block
				|```
			""",
			"""
				|```
				|<3ba5b99385d0631cf04fef13a9c76cd4b0ee0143>
				|```
			""",
		)
	}

	@Test
	fun testCodeBlock2() {
		assert(
			"""
				|```
				|
				|a
				|code
				|block
				|```
			""",
			"""
				|```
				|<ea5fdc1146ca984eab6e5846a1504c148bddb250>
				|```
			""",
		)
	}

	@Test
	fun testCodeBlock3() {
		assert(
			"""
				|```
				|a
				|code
				|block
				|
				|```
			""",
			"""
				|```
				|<d5b4e4a5509bb832392dce376041915c8445bdc4>
				|```
			""",
		)
	}

	@Test
	fun testCodeBlock4() {
		assert(
			"""
				|Some text:
				|
				|```
				|a
				|code
				|block
				|```
				|
				|more text
			""",
			"""
				|Some text:
				|
				|```
				|<3ba5b99385d0631cf04fef13a9c76cd4b0ee0143>
				|```
				|
				|more text
			""",
		)
	}

	@Test
	fun testCodeBlock5() {
		assert(
			"""
				|Some text:
				|```
				|a
				|code
				|block
				|```
				|more text
			""",
			"""
				|Some text:
				|```
				|<3ba5b99385d0631cf04fef13a9c76cd4b0ee0143>
				|```
				|more text
			""",
		)
	}

	@Test
	fun testBlockQuote1() {
		assert(
			"""
				|> quote quote
				|> quote
			""",
			"""
				|> <83f42cc522250232474de01e7b02207921713ece>
			""",
		)
	}

	@Test
	fun testBlockQuote2() {
		assert(
			"""
				|>
				|> quote quote
				|> quote
			""",
			"""
				|> <90cd98b5dcdfd18ab1734af65a07114f0d279771>
			""",
		)
	}

	@Test
	fun testBlockQuote3() {
		assert(
			"""
				|> quote quote
				|> quote
				|>
			""",
			"""
				|> <cd503b621fc6cca39295258cdd606e9c0966b7c0>
			""",
		)
	}

	@Test
	fun testBlockQuote4() {
		assert(
			"""
				|Some text:
				|
				|> quote quote
				|> quote
				|>
				|
				|more text
			""",
			"""
				|Some text:
				|
				|> <cd503b621fc6cca39295258cdd606e9c0966b7c0>
				|
				|more text
			""",
		)
	}

	@Test
	fun testBlockQuote5() {
		assert(
			"""
				|Some text:
				|> quote quote
				|> quote
				|more text
			""",
			"""
				|Some text:
				|> <99e77f9d754834abef7e1117c335a47629e480a>
			""",
		)
	}

	@Test
	fun testPreserveNewLinesEmpty() {
		assert(
			"""
				|
				|	
				|
			""",
			"""
				|
				|	
				|
			""",
		)
	}

	@Test
	fun testPreserveNewLines1() {
		assert(
			"""
				|# Configuration
				|	
				|## Directory
			""",
			"""
				|# Configuration
				|	
				|## Directory
			""",
		)
	}

	@Test
	fun testPreserveNewLines2() {
		assert(
			"""
				|Configuration
				|
				|	
				|## Directory
			""",
			"""
				|Configuration
				|
				|	
				|## Directory
			""",
		)
	}

	@Test
	fun testPreserveNewLines3() {
		assert(
			"""
				|# Configuration
				|
				|	
				|Directory
			""",
			"""
				|# Configuration
				|
				|	
				|Directory
			""",
		)
	}

	@Test
	fun testPreserveNewLines4() {
		assert(
			"""
				|- *foo*
				|- **bar**
				|- _baz_
			""",
			"""
				|- *foo*
				|- **bar**
				|- _baz_
			""",
		)
	}

	@Test
	fun testPreserveNewLinesAtBeginning() {
		assert(
			"""
				|
				|
				|
				|# Configuration
			""",
			"""
				|
				|
				|
				|# Configuration
			""",
		)
	}

	@Test
	fun testPreserveNewLinesAtEnd() {
		assert(
			"""
				|# Configuration
				|
				|
				|
			""",
			"""
				|# Configuration
				|
				|
				|
			""",
		)
	}

	private fun assert(rawText: String, rawExpected: String) {
		assert(FilteredText.Part.of(rawText.trimMargin()), rawExpected, rawExpected)
	}

	private fun assert(raw: FilteredText.Part, fullExpected: String, partExpected: String): FilteredText {
		val filtered = FilteredText.filter(raw)
		Assertions.assertEquals(fullExpected.trimMargin(), filtered.clean.all)
		Assertions.assertEquals(partExpected.trimMargin(), filtered.clean.extract)
		return filtered
	}
}