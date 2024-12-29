/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.parser.*
import com.vladsch.flexmark.util.ast.*
import com.vladsch.flexmark.util.data.*
import dev.aibtra.core.*
import dev.aibtra.diff.*
import dev.aibtra.refiner.*
import dev.aibtra.text.*
import org.json.simple.*
import org.json.simple.parser.*
import java.io.*
import java.util.function.*

class OpenAIRefinementService(apiToken: String, debugLog: DebugLog) : OpenAIService(apiToken, debugLog) {
	fun request(profile: OpenAIRefinementConfiguration.Profile, part: FilteredText.Part, rawPriorConversation: RefinerConversation?, macroResolver: MacroResolver, callback: (result: Result) -> Boolean) {
		val streaming = profile.streaming
		val selectionMode = part.isPart()
		val responseType = profile.responseType
		val followUpInstructions = profile.followUpInstructions
		val (instructions, priorConversation) = if (rawPriorConversation != null && followUpInstructions != null) {
			Pair(followUpInstructions, rawPriorConversation)
		}
		else {
			Pair(profile.mainInstructions, null)
		}

		val newMessages = mutableListOf<JSONObject>()
		for (instruction in instructions) {
			if (!instruction.mode.matches(selectionMode)) {
				continue
			}

			val resolution = HashMap<String, String>()
			val content = macroResolver.replace(instruction.text, resolution)
			newMessages.add(createMessage(content, instruction.role))
		}

		val messageArray = JSONArray()
		priorConversation?.let { conversation ->
			for (entry in conversation.entries) {
				(entry as? ConversationEntry)?.let {
					messageArray.addAll(it.messages)
				}
			}
		}
		messageArray.addAll(newMessages)

		val content = part.extract
		val handler = if (streaming) {
			object : StreamingHandler {
				override fun process(builder: StringBuilder): Boolean {
					return callback(Result(builder.toString(), null))
				}

				override fun finish(builder: StringBuilder) {
					processResponse(builder.toString(), content, responseType, priorConversation, newMessages, callback)
				}
			}
		}
		else {
			object : ResultHandler {
				override fun process(message: String) {
					processResponse(message, content, responseType, priorConversation, newMessages, callback)
				}
			}
		}

		request(profile.model, messageArray, handler, object : FailureHandler {
			override fun process(failure: IOException, mightBeAuthentication: Boolean) {
				callback(Result(null, null, Pair(failure, mightBeAuthentication)))
			}
		})
	}

	private fun processResponse(message: String, content: String, responseType: OpenAIRefinementConfiguration.ResponseType, priorConversation: RefinerConversation?, newMessages: List<JSONObject>, callback: (result: Result) -> Boolean) {
		// We always have to create a conversation:
		// - to signal that a streaming request is now finished (triggering a final ref-update)
		// - to be able to display the output for Show Full Response
		val conversation = createConversation(message, priorConversation, newMessages)
		if (responseType == OpenAIRefinementConfiguration.ResponseType.CONTENT_FENCED) {
			val response = extractLastFencedCodeBlock(message).let {
				val fixed = StringUtils.fixIndentation(it, content)
				ensureLeadingAndTrailingWhitespaces(content, fixed)
			}
			callback(Result(response, conversation))
		}
		else {
			val response = applyFixes(content, message)
			callback(Result(response, conversation))
		}
	}

	private fun applyFixes(content: String, result: String): String {
		return dropMarkdownPrefix(content, result)
			?: ensureLeadingAndTrailingWhitespaces(content, result)
	}

	private fun ensureLeadingAndTrailingWhitespaces(content: String, result: String): String {
		val leadingWhitespaces = content.takeWhile { it.isWhitespace() }
		val trailingWhitespaces = content.takeLastWhile { it.isWhitespace() }
		val trimmedResult = result.trim()
		return leadingWhitespaces +
						trimmedResult +
						trailingWhitespaces
	}

	private fun dropMarkdownPrefix(content: String, result: String): String? {
		if (!MARKDOWN_PREFIX_PATTERN.containsMatchIn(content)) {
			return null
		}

		return result
			.replace(MARKDOWN_PREFIX_PATTERN, "")
			.replace(MARKDOWN_SUFFIX_PATTERN, "")
	}

	class Result(val content: String?, val conversation: RefinerConversation?, val failure: Pair<IOException, Boolean>? = null)

	class ConversationEntry(override val title: String, val messages: List<JSONObject>) : RefinerConversation.Entry

	companion object {
		private const val TITLE_MAX_LENGTH = 128
		private val LOG = Logger.getLogger(this::class)

		val MARKDOWN_PREFIX_PATTERN = Regex("^\\s*```(\\w+)?\n")
		val MARKDOWN_SUFFIX_PATTERN = Regex("```\\s*$")

		internal fun applyJson(input: String, content: String, focusStart: Int): String {
			return when (val obj = parseJson(input)) {
				is JSONObject -> applyJsonPatch(obj, content, focusStart)
				is JSONArray -> applyJsonPatch(obj, content, focusStart)
				else -> throw IOException("Invalid JSON response: root object missing")
			}
		}

		private fun createConversation(message: String, priorConversation: RefinerConversation?, newMessages: List<JSONObject>): RefinerConversation {
			val firstMessage = newMessages[0]["content"] as String
			val substring = firstMessage.take(TITLE_MAX_LENGTH)
			val title = if (firstMessage.length > TITLE_MAX_LENGTH) "$substring..." else substring
			val priorEntries: List<ConversationEntry> = priorConversation?.entries?.map { it as ConversationEntry } ?: listOf()
			val assistantMessage = createMessage(message, OpenAIRole.ASSISTANT)
			return RefinerConversation(priorEntries + listOf(ConversationEntry(title, newMessages + assistantMessage)))
		}

		private fun extractLastFencedCodeBlock(raw: String): String {
			val options = MutableDataSet()
			options.set(Parser.BLANK_LINES_IN_AST, true)

			val parser: Parser = Parser.builder(options).build()
			val node = parser.parse(raw)
			var lastFencedCodeBlock: String? = null
			object : NodeVisitor() {
				override fun processNode(node: Node, withChildren: Boolean, processor: BiConsumer<Node, Visitor<Node>>) {
					super.processNode(node, withChildren, processor)

					if (node is FencedCodeBlock) {
						val content = node.contentChars.toString()
						lastFencedCodeBlock = content
					}
				}
			}.visit(node)
			return lastFencedCodeBlock ?: throw IOException("Could not detect fenced code block")
		}

		private fun parseJson(input: String): Any {
			parseRawJson(input)?.let { return it }

			val options = MutableDataSet()
			options.set(Parser.BLANK_LINES_IN_AST, true)

			val parser: Parser = Parser.builder(options).build()
			val document = parser.parse(input)
			for (child in document.children) {
				(child as? FencedCodeBlock)?.let { block ->
					parseRawJson(block.contentChars.toString())?.let {
						return it
					}
				}
			}

			LOG.error("Invalid JSON response:")
			LOG.error(input)
			throw IOException("Invalid JSON response")
		}

		private fun parseRawJson(json: String): Any? {
			val parser = JSONParser()
			try {
				return parser.parse(json)
			} catch (_: ParseException) {
			}

			try {
				return parser.parse(json.replace(Regex("(?m)^.*oldLineStart.*$\\n?"), ""))
			} catch (_: Exception) {
			}

			return null
		}

		private fun applyJsonPatch(arr: JSONArray, content: String, focusStart: Int): String {
			return arr.fold(content) { acc, any ->
				val jsonObject = any as? JSONObject
					?: throw IOException("Invalid JSON response: no object array")
				applyJsonPatch(jsonObject, acc, focusStart)
			}
		}

		private fun applyJsonPatch(root: JSONObject, content: String, focusStart: Int): String {
			val old = root["old"] as? String
			val oldLineStart = root["oldLineStart"] as? Long
			val new = root["new"] as? String
			if (old == null || new == null) {
				when (val element = root.values.singleOrNull()) {
					is JSONArray -> return applyJsonPatch(element, content, focusStart)
					else -> {
						LOG.info("ROOT:")
						LOG.info(JsonUtils.formatJson(root.toJSONString()))
						throw IOException("Invalid JSON response: unexpected format")
					}
				}
			}

			val exactIndex = content.indexOf(old, focusStart)
			if (exactIndex >= 0) {
				val nextIndex = content.indexOf(old, exactIndex + 1)
				if (nextIndex >= 0) {
					require(nextIndex > exactIndex)
					LOG.warn("Old content found multiple times.\n\noldLineStart would be $oldLineStart")
				}

				return content.substring(0, exactIndex) + new + content.substring(exactIndex + old.length)
			}

			LOG.warn("Old content not reported back precisely, now conducting a fuzzy search.")

			// Sometimes o1-preview won't report the replaced block exactly
			//
			// For example line:
			// FILE_PREFIX="${1#--prefix=}"
			// becomes:
			// FILE_PREFIX="\${1#--prefix=}"
			//
			// I've seen this for o1-mini, too, like where it would fix a typo in a comment:
			//     echo "Don't use '*' in the prefix because it will be exanded before the script is called!" >&2
			// becomes:
			//     echo "Don't use '*' in the prefix because it will be expanded before the script is called!" >&2
			//
			// o-mini may return blocks with identation removed (see testChangedIndentation)
			val matcher = FuzzyMatcher.findBestMatch(content, old, focusStart, 16, old.length / 64)
			val index = matcher.from
			if (index < 0) {
				LOG.info("OLD (as reported by OpenAI):")
				LOG.info(old)
				LOG.info("CONTENT:")
				LOG.info(content)
				throw IOException("Invalid JSON response: old block can't be identified")
			}

			val lineNumber = getLineNumber(content, index)
			if (oldLineStart == null || lineNumber != oldLineStart.toInt()) {
				// Sometimes the reported line number is quite off
				LOG.warn("Line number mismatch: expected: $oldLineStart, actual: $lineNumber")
			}

			return content.substring(0, matcher.from) +
							new +
							content.substring(matcher.to)
		}

		private fun getLineNumber(s: String, index: Int): Int {
			require(index in s.indices) { "Index out of bounds" }
			var lineNumber = 1
			for (i in 0 until index) {
				if (s[i] == '\n') {
					lineNumber++
				}
			}
			return lineNumber
		}
	}
}