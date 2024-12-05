/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.parser.*
import com.vladsch.flexmark.util.data.*
import dev.aibtra.core.*
import dev.aibtra.diff.*
import org.json.simple.*
import org.json.simple.parser.*
import java.io.*

class OpenAIRefinementService(apiToken: String, debugLog: DebugLog) : OpenAIService(apiToken, debugLog) {
	fun request(profile: OpenAIRefinementConfiguration.Profile, selection: Selection?, macroResolver: MacroResolver, callback: (result: Result) -> Boolean) {
		val streaming = profile.streaming
		val contentMacro = when {
			profile.responseType == OpenAIRefinementConfiguration.ResponseType.SELECTION -> OpenAIRefinementConfiguration.SELECTION_MACRO
			profile.responseType == OpenAIRefinementConfiguration.ResponseType.CONTENT -> OpenAIRefinementConfiguration.CONTENT_MACRO
			isPatchResponseType(profile.responseType) -> OpenAIRefinementConfiguration.CONTENT_MACRO
			else -> throw NoWhenBranchMatchedException()
		}

		val selectionMode = selection != null
		val responseType = if (selectionMode) {
			profile.responseType
		}
		else {
			OpenAIRefinementConfiguration.ResponseType.CONTENT
		}

		if (streaming && isPatchResponseType(responseType)) {
			throw IOException("Can't combine response type '$responseType' with 'streaming'.")
		}
		
		val messages = JSONArray()
		var contentVar : String? = null
		for (instruction in profile.instructions) {
			if (!instruction.mode.matches(selectionMode)) {
				continue
			}

			val resolution = HashMap<String, String>()
			val content = macroResolver.replace(instruction.text, resolution)
			resolution[contentMacro]?.let {
				contentVar = it
			}

			messages.add(createMessage(content, instruction.role))
		}

		val content = contentVar ?: throw IOException("Profile instructions don't extract any content to send. Are you missing the \${CONTENT} keyword?")
		val handler = if (streaming) {
			object : StreamingHandler {
				override fun process(builder: StringBuilder): Boolean {
					return callback(Result(builder.toString(), false))
				}

				override fun finish(builder: StringBuilder) {
					if (applyFixes(content, builder, responseType)) {
						callback(Result(builder.toString(), true))
					}
				}
			}
		}
		else {
			object : ResultHandler {
				override fun process(message: String) {
					val res = if (responseType == OpenAIRefinementConfiguration.ResponseType.SELECTION_JSON) {
						applyJson(message, content, selection?.from ?: 0)
					}
					else {
						val builder = StringBuilder(message)
						applyFixes(content, builder, responseType)
						builder.toString()
					}

					callback(Result(res, true))
				}
			}
		}

		request(profile.model, messages, handler, object : FailureHandler {
			override fun process(failure: IOException, mightBeAuthentication: Boolean) {
				callback(Result(null, false, Pair(failure, mightBeAuthentication)))
			}
		})
	}

	private fun applyFixes(content: String, result: StringBuilder, responseType: OpenAIRefinementConfiguration.ResponseType): Boolean {
		if (dropMarkdownPrefix(content, result)) {
			return true
		}

		if (isPatchResponseType(responseType)) {
			return false
		}

		return ensureLeadingAndTrailingWhitespaces(content, result)
	}

	private fun ensureLeadingAndTrailingWhitespaces(content: String, result: StringBuilder): Boolean {
		val leadingWhitespaces = content.takeWhile { it.isWhitespace() }
		val trailingWhitespaces = content.takeLastWhile { it.isWhitespace() }
		val trimmedResult = result.toString().trim()

		result.clear()
		result.append(leadingWhitespaces)
		result.append(trimmedResult)
		result.append(trailingWhitespaces)
		return true
	}

	private fun dropMarkdownPrefix(content: String, result: StringBuilder): Boolean {
		if (MARKDOWN_PREFIX_PATTERN.containsMatchIn(content)) {
			return false
		}

		if (!MARKDOWN_PREFIX_PATTERN.containsMatchIn(result) || !MARKDOWN_SUFFIX_PATTERN.containsMatchIn(result)) {
			return false
		}

		MARKDOWN_SUFFIX_PATTERN.find(result)?.let {
			result.delete(it.range.first, result.length)
		}

		MARKDOWN_PREFIX_PATTERN.find(result)?.let {
			result.delete(0, it.range.last + 1)
		}

		return true
	}

	class Result(val content: String?, val finished: Boolean, val failure: Pair<IOException, Boolean>? = null)

	class Selection(val from: Int)

	companion object {
		private val LOG = Logger.getLogger(this::class)

		val MARKDOWN_PREFIX_PATTERN = Regex("^\\s*```(\\w+)?\n")
		val MARKDOWN_SUFFIX_PATTERN = Regex("```\\s*$")

		fun isPatchResponseType(type: OpenAIRefinementConfiguration.ResponseType) : Boolean {
			return type == OpenAIRefinementConfiguration.ResponseType.SELECTION_JSON
		}

		internal fun applyJson(input: String, content: String, focusStart: Int): String {
			val obj = parseJson(input)
			return when (obj) {
				is JSONObject -> applyJsonPatch(obj, content, focusStart)
				is JSONArray -> applyJsonPatch(obj, content, focusStart)
				else -> throw IOException("Invalid JSON response: root object missing")
			}
		}

		private fun parseJson(input: String): Any {
			parseRawJson(input)?.let { return it }

			val options = MutableDataSet()
			options.set(Parser.BLANK_LINES_IN_AST, true)

			val parser: Parser = Parser.builder(options).build()
			val document = parser.parse(input)
			for (child in document.children) {
				(child as? FencedCodeBlock)?.let {
					parseRawJson(it.contentChars.toString())?.let { return it }
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