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
	val clean: Part,
	private val placeholderToText: Map<String, String>,
	private val rawFilteredIndexes: Set<Int>
) {
	fun recreate(replacement: String, mode: RecreateMode): String {
		var recreated = when (mode) {
			RecreateMode.FULL -> replacement
			RecreateMode.PART_PREFIX -> clean.all.substring(0, clean.from) + replacement
			else -> clean.all.substring(0, clean.from) + replacement + clean.all.substring(clean.to, clean.length)
		}

		for (placeholder in placeholderToText.keys) {
			recreated = recreated.replace(placeholder, placeholderToText[placeholder]!!)
		}

		return recreated
	}

	fun isFiltered(index: Int): Boolean {
		return rawFilteredIndexes.contains(index)
	}

	companion object {
		fun asIs(raw: Part): FilteredText {
			return FilteredText(raw, mapOf(), setOf())
		}

		fun filter(raw: Part): FilteredText {
			val options = MutableDataSet()
			options.set(Parser.BLANK_LINES_IN_AST, true)

			val parser: Parser = Parser.builder(options).build()
			val node: Node = parser.parse(raw.all)

			val from = Index(raw.from, IndexType.FROM)
			val to = Index(raw.to, IndexType.TO)
			val indexToMapped: MutableMap<Index, Int?> = mutableMapOf(from to null, to to null)
			val visitor = StringBuilderVisitor(raw.all, indexToMapped)
			visitor.visit(node)

			val clean = visitor.finish()
			val fromMapped = requireNotNull(indexToMapped[from])
			val toMappend = requireNotNull(indexToMapped[to])
			val filtered = Part(clean, fromMapped, toMappend)
			return FilteredText(filtered, visitor.placeholderToText, visitor.rawFilteredIndexes)
		}
	}

	private class StringBuilderVisitor(val text: String, val indexToMapped: MutableMap<Index, Int?>) : NodeVisitor() {
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
				appendChars(node.chars.toString(), node.startOffset, node.endOffset, false)
			}
			else {
				super.processNode(node, withChildren, processor)
			}
		}

		fun finish(): String {
			appendChars("", text.length, text.length, false)
			return builder.toString()
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

			appendChars(raw.substring(0, wsPrefix) + placeholder + raw.substring(raw.length - wsSuffix, raw.length), node.startOffset, node.endOffset, true)
		}

		private fun appendChars(chars: String, startOffset: Int, endOffset: Int, cleaned: Boolean) {
			append(text.subSequence(lastOffset, startOffset), false, lastOffset, startOffset)
			append(chars, cleaned, startOffset, endOffset)
			lastOffset = endOffset
		}

		private fun append(chars: CharSequence, cleaned: Boolean, startOffset: Int, endOffset: Int) {
			val from = builder.length
			val to = from + chars.length
			indexToMapped.forEach { entry ->
				val index = entry.key
				val pos = index.pos
				val type = index.type
				val transformed = from + (pos - startOffset)
				val mapped = if (type == IndexType.FROM &&
					(pos in startOffset until endOffset || pos == builder.length)) {
					if (cleaned) {
						from
					}
					else {
						transformed
					}
				}
				else if (type == IndexType.TO && pos in startOffset..endOffset) {
					if (cleaned) {
						to
					}
					else {
						transformed
					}
				}
				else {
					null
				}

				mapped?.let {
					indexToMapped[index] = mapped
				}
			}
			builder.append(chars)
		}
	}

	data class Part(val all: String, internal val from: Int, internal val to: Int) {
		val length: Int
			get() = all.length
		val extract: String
			get() = all.substring(from, to)

		init {
			require(from in 0..to)
			require(to <= all.length)
		}

		fun isPart(): Boolean {
			return from > 0 || to < all.length
		}

		companion object {
			fun of(text: String): Part {
				return Part(text, 0, text.length)
			}
		}
	}

	enum class RecreateMode {
		FULL, PART, PART_PREFIX
	}

	private enum class IndexType {
		FROM, TO
	}

	private data class Index(val pos: Int, val type: IndexType)
}