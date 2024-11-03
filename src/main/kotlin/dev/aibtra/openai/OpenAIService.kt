/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import dev.aibtra.core.DebugLog
import dev.aibtra.core.JsonUtils
import dev.aibtra.core.JsonUtils.Companion.objNotNull
import dev.aibtra.core.Logger
import dev.aibtra.diff.FuzzyMatcher
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets

class OpenAIService(private val apiToken: String, private val debugLog: DebugLog) {
	fun request(profile: OpenAIConfiguration.Profile, selection: Selection?, keywordResolver: (key: String) -> String?, callback: (result: Result) -> Boolean) {
		val input = JSONObject()
		input["model"] = profile.model
		input["n"] = 1

		val streaming = profile.streaming
		if (streaming) {
			input["stream"] = true
		}

		val contentKeyword = when {
			profile.responseType == OpenAIConfiguration.ResponseType.SELECTION -> OpenAIConfiguration.SELECTION_KEYWORD
			profile.responseType == OpenAIConfiguration.ResponseType.CONTENT -> OpenAIConfiguration.CONTENT_KEYWORD
			isPatchResponseType(profile.responseType) -> OpenAIConfiguration.CONTENT_KEYWORD
			else -> throw NoWhenBranchMatchedException()
		}

		val selectionMode = selection != null
		val responseType = if (selectionMode) {
			profile.responseType
		}
		else {
			OpenAIConfiguration.ResponseType.CONTENT
		}

		if (streaming && isPatchResponseType(responseType)) {
			throw IOException("Can't combine response type '$responseType' with 'streaming'.")
		}
		
		val messagesIn = JSONArray()
		var contentVar : String? = null
		for (instruction in profile.instructions) {
			if (!instruction.mode.matches(selectionMode)) {
				continue
			}

			val messageIn = JSONObject()
			messageIn["role"] = instruction.role.id
			messageIn["content"] = KEYWORD_REGEX.replace(instruction.text) { matchResult ->
				val key = matchResult.groupValues[1]
				val value = keywordResolver(key) ?: throw IOException("Unknown keyword '$key'")
				if (key == contentKeyword) {
					contentVar = value
				}
				value
			}
			messagesIn.add(messageIn)
		}
		input["messages"] = messagesIn

		val content = contentVar ?: throw IOException("Profile instructions don't extract any content to send. Are you missing the \${CONTENT} keyword?")
		val url = URI("https://api.openai.com/v1/chat/completions").toURL()
		val connection = url.openConnection() as HttpURLConnection
		try {
			connection.doOutput = true
			connection.requestMethod = "POST"
			connection.addRequestProperty("Authorization", "Bearer $apiToken")
			connection.addRequestProperty("Content-Type", "application/json")

			debugLog.run("openai", DebugLog.Level.INFO) { log: DebugLog.Log, _: Boolean ->
				val jsonInput = input.toJSONString()
				log.println("SEND: ")
				log.println(JsonUtils.formatJson(jsonInput))

				log.println("")
				log.println("RECEIVE: ")
				try {
					connection.outputStream.use { output ->
						output.write(jsonInput.toByteArray(StandardCharsets.UTF_8))
						output.flush()

						connection.inputStream.use { input ->
							if (streaming) {
								val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
								val builder = StringBuilder()
								while (true) {
									val line = reader.readLine() ?: break
									log.println(line)

									if (line.startsWith("data: ")) {
										val data = line.substring(6)
										if (data == "[DONE]") {
											break
										}

										if (!parseDataChunk(data, builder, callback)) {
											break
										}
									}
								}

								if (applyFixes(content, builder, responseType)) {
									callback(Result(builder.toString(), true))
								}
							}
							else {
								InputStreamReader(input, StandardCharsets.UTF_8).use {
									val parser = JSONParser()
									val result = parser.parse(it)
									log.println(result.toString())

									val choices = objNotNull<JSONArray>(result, "choices")
									if (choices.size != 1) {
										throw IOException("Unexpected number of 'choices'")
									}
									val choice = requireNotNull(choices[0])
									val messageOut = objNotNull<JSONObject>(choice, "message")
									val message = objNotNull<String>(messageOut, "content")
									val res = if (responseType == OpenAIConfiguration.ResponseType.SELECTION_JSON) {
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

							Unit
						}
					}
				} catch (ioe: IOException) {
					val parser = JSONParser()
					val mightBeAuthentication = connection.responseCode in AUTHENTICATION_RELATED_RESPONSE_CODES
					connection.errorStream?.let { errorStream ->
						(parser.parse(InputStreamReader(errorStream, StandardCharsets.UTF_8)) as? JSONObject)?.let { result ->
							(result["error"] as? JSONObject)?.let { error ->
								(error["message"] as? String)?.let { message ->
									callback(Result(null, false, Pair(IOException("${ioe.message}:\n\n$message"), mightBeAuthentication)))
								}
							}
						}
					} ?: run {
						if (ioe is UnknownHostException) {
							callback(Result(null, false, Pair(IOException("Unknown host: ${ioe.message}"), false)))
						}
						else {
							callback(Result(null, false, Pair(ioe, mightBeAuthentication)))
						}
					}
				}
			}
		} finally {
			connection.disconnect()
		}
	}

	private fun applyFixes(content: String, result: StringBuilder, responseType: OpenAIConfiguration.ResponseType): Boolean {
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

	private fun parseDataChunk(data: String, builder: StringBuilder, callback: (result: Result) -> Boolean): Boolean {
		return StringReader(data).use {
			val parser = JSONParser()
			val result = parser.parse(it)
			val choices = objNotNull<JSONArray>(result, "choices")
			if (choices.size != 1) {
				throw IOException("Unexpected number of 'choices'")
			}

			val choice = requireNotNull(choices[0])
			val messageOut = objNotNull<JSONObject>(choice, "delta")
			if (JsonUtils.objMaybeNull<String>(choice, "finish_reason") == null) {
				val message = objNotNull<Any>(messageOut, "content")
				builder.append(message)
				callback(Result(builder.toString(), false))
			}
			else {
				false
			}
		}
	}

	class Result(val content: String?, val finished: Boolean, val failure: Pair<IOException, Boolean>? = null)

	class Selection(val from: Int)

	companion object {
		private val LOG = Logger.getLogger(this::class)

		val AUTHENTICATION_RELATED_RESPONSE_CODES = setOf(HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN)
		val KEYWORD_REGEX = "\\$\\{(\\w+)}".toRegex()
		val MARKDOWN_PREFIX_PATTERN = Regex("^\\s*```(\\w+)?\n")
		val MARKDOWN_SUFFIX_PATTERN = Regex("```\\s*$")

		fun isPatchResponseType(type: OpenAIConfiguration.ResponseType) : Boolean {
			return type == OpenAIConfiguration.ResponseType.SELECTION_JSON
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