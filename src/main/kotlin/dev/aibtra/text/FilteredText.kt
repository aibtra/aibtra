/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.text

import com.vladsch.flexmark.ast.BlockQuote
import com.vladsch.flexmark.ast.Code
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Document
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.Visitor
import com.vladsch.flexmark.util.data.MutableDataSet
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.function.BiConsumer

class FilteredText(
	val clean: String,
	private val placeholderToText: Map<String, String>,
	private val rawFilteredIndexes: Set<Int>
) {
	fun recreate(filtered: String): String {
		var recreated = filtered
		for (placeholder in placeholderToText.keys) {
			recreated = recreated.replace(placeholder, placeholderToText[placeholder]!!)
		}

		return recreated
	}

	fun isFiltered(index: Int): Boolean {
		return rawFilteredIndexes.contains(index)
	}

	companion object {
		fun asIs(raw: String): FilteredText {
			return FilteredText(raw, mapOf(), setOf())
		}

		fun filter(raw: String): FilteredText {
			val options = MutableDataSet()
			options.set(Parser.BLANK_LINES_IN_AST, true)

			val parser: Parser = Parser.builder(options).build()
			val node: Node = parser.parse(raw)

			val visitor = StringBuilderVisitor(raw)
			visitor.visit(node)
			visitor.finish()
			return FilteredText(visitor.builder.toString(), visitor.placeholderToText, visitor.rawFilteredIndexes)
		}
	}

	private class StringBuilderVisitor(val text: String) : NodeVisitor() {
		private val digest = MessageDigest.getInstance("SHA-1")

		val builder = StringBuilder()
		val placeholderToText = mutableMapOf<String, String>()
		private val textToPlaceholder = mutableMapOf<String, String>()
		val rawFilteredIndexes = mutableSetOf<Int>()

		private var lastOffset = 0

		override fun processNode(node: Node, withChildren: Boolean, processor: BiConsumer<Node, Visitor<Node>>) {
			if (node is Document) {
				super.processNode(node, withChildren, processor)
				return
			}

			if (node is Code) {
				appendPlaceholder("`", node, "`")
			}
			else if (node is FencedCodeBlock) {
				appendPlaceholder("```\n", node, "\n```")
			}
			else if (node is BlockQuote) {
				appendPlaceholder("> ", node, "")
			}
			else if (!node.hasChildren()) {
				appendChars(node.chars.toString(), node.startOffset, node.endOffset)
			}
			else {
				super.processNode(node, withChildren, processor)
			}
		}

		fun finish() {
			appendChars("", text.length, text.length)
		}

		private fun appendPlaceholder(prefix: String, node: Node, suffix: String) {
			val raw = node.chars.toString()
			for (i in node.startOffset until node.endOffset) {
				rawFilteredIndexes.add(i)
			}

			var wsPrefix = 0
			for (c in raw) {
				if (!Character.isWhitespace(c)) {
					break
				}
				wsPrefix++
			}

			var wsSuffix = 0
			for (i in raw.length - 1 downTo 0) {
				if (!Character.isWhitespace(raw[i])) {
					break
				}
				wsSuffix++
			}

			val org = raw.substring(wsPrefix, raw.length - wsSuffix)
			val hash = textToPlaceholder.computeIfAbsent(org) {
				BigInteger(1, digest.digest(org.toByteArray(StandardCharsets.UTF_8))).toString(16)
			}

			val placeholder = "$prefix<$hash>$suffix"
			val existing = placeholderToText.put(placeholder, org)
			require(existing == null || existing == org)

			appendChars(raw.substring(0, wsPrefix) + placeholder + raw.substring(raw.length - wsSuffix, raw.length), node.startOffset, node.endOffset)
		}

		private fun appendChars(chars: String, startOffset: Int, endOffset: Int) {
			builder.append(text.subSequence(lastOffset, startOffset))
			builder.append(chars)
			lastOffset = endOffset
		}
	}
}