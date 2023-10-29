/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.core.JsonUtils
import dev.aibtra.core.JsonUtils.Companion.objNotNull
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets

class OpenAIService(private val apiToken: String) {
	fun request(model: String, content: String, streaming: Boolean, callback: (result: Result) -> Boolean) {
		val input = JSONObject()
		input["model"] = model
		input["n"] = 1
		if (streaming) {
			input["stream"] = true
		}

		val messagesIn = JSONArray()
		val messageIn = JSONObject()
		messageIn["role"] = "user"
		messageIn["content"] = content
		messagesIn.add(messageIn)
		input["messages"] = messagesIn

		val url = URL("https://api.openai.com/v1/chat/completions")
		val connection = url.openConnection() as HttpURLConnection
		try {
			connection.doOutput = true
			connection.requestMethod = "POST"
			connection.addRequestProperty("Authorization", "Bearer $apiToken")
			connection.addRequestProperty("Content-Type", "application/json")

			val jsonInput = input.toJSONString()
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

							if (ensureTrailingNewlines(content, builder)) {
								callback(Result(builder))
							}
						}
						else {
							InputStreamReader(input, StandardCharsets.UTF_8).use {
								val parser = JSONParser()
								val result = parser.parse(it)
								val choices = objNotNull<JSONArray>(result, "choices")
								if (choices.size != 1) {
									throw IOException("Unexpected number of 'choices'")
								}
								val choice = requireNotNull(choices[0])
								val messageOut = objNotNull<JSONObject>(choice, "message")
								val message = objNotNull<String>(messageOut, "content")
								val builder = StringBuilder(message)
								ensureTrailingNewlines(content, builder)
								callback(Result(builder))
							}
						}

						return
					}
				}
			} catch (ioe: IOException) {
				val parser = JSONParser()
				val mightBeAuthentication = connection.responseCode in AUTHENTICATION_RELATED_RESPONSE_CODES
				connection.errorStream?.let { errorStream ->
					(parser.parse(InputStreamReader(errorStream, StandardCharsets.UTF_8)) as? JSONObject)?.let { result ->
						(result["error"] as? JSONObject)?.let { error ->
							(error["message"] as? String)?.let { message ->
								callback(Result(null, Pair(IOException("${ioe.message}:\n\n$message"), mightBeAuthentication)))
							}
						}
					}
				} ?: run {
					if (ioe is UnknownHostException) {
						callback(Result(null, Pair(IOException("Unknown host: ${ioe.message}"), false)))
					}
					else {
						callback(Result(null, Pair(ioe, mightBeAuthentication)))
					}
				}
			}
		} finally {
			connection.disconnect()
		}
	}

	private fun ensureTrailingNewlines(content: String, result: StringBuilder): Boolean {
		val inputCount = content.takeLastWhile { it == '\n' }.count()
		val resultCount = result.takeLastWhile { it == '\n' }.count()
		if (resultCount >= inputCount) {
			return false
		}

		result.append("\n".repeat(inputCount - resultCount))
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
				callback(Result(builder))
			}
			else {
				false
			}
		}
	}

	class Result(val content: StringBuilder?, val failure: Pair<IOException, Boolean>? = null)

	companion object {
		val AUTHENTICATION_RELATED_RESPONSE_CODES = setOf(HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN)
	}
}