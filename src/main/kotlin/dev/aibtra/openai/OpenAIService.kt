/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

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
	fun request(model: String, systemMessage: String, userMessage: String, streaming: Boolean, callback: (result: Result) -> Boolean) {
		val input = JSONObject()
		input["model"] = model
		input["n"] = 1
		if (streaming) {
			input["stream"] = true
		}

		val messagesIn = JSONArray()
//		messagesIn.add(createMessageObject("system", "Do not change the markdown structure"))
//		messagesIn.add(createMessageObject("system", "Preserve the detected language"))
//		messagesIn.add(createMessageObject("system", "Preserve the markdown structure exactly"))
//		messagesIn.add(createMessageObject("system", "Stay as close as possible to the original"))
		messagesIn.add(createMessageObject("system", "Proofread following text and stay as close as possible to the original."))
		messagesIn.add(createMessageObject("user", userMessage))

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
								callback(Result(StringBuilder(message)))
							}
						}
					}
				}
			} catch (ioe: IOException) {
				val parser = JSONParser()
				connection.errorStream?.let { errorStream ->
					(parser.parse(InputStreamReader(errorStream, StandardCharsets.UTF_8)) as? JSONObject)?.let { result ->
						(result["error"] as? JSONObject)?.let { error ->
							(error["message"] as? String)?.let { message ->
								callback(Result(null, Pair(IOException("${ioe.message}:\n\n$message"), true)))
							}
						}
					}
				} ?: run {
					if (ioe is UnknownHostException) {
						callback(Result(null, Pair(IOException("Unknown host: ${ioe.message}"), false)))
					}
					else {
						callback(Result(null, Pair(ioe, true)))
					}
				}
			}
		} finally {
			connection.disconnect()
		}
	}

	private fun createMessageObject(role: String, userMessage: String): JSONObject {
		val obj = JSONObject()
		obj["role"] = role
		obj["content"] = userMessage
		return obj
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
			if (objMaybeNull<String>(choice, "finish_reason") == null) {
				val message = objNotNull<Any>(messageOut, "content")
				builder.append(message)
				callback(Result(builder))
			}
			else {
				false
			}
		}
	}

	private fun <T> objNotNull(input: Any, key: String): T {
		return objMaybeNull<T>(input, key) ?: throw IOException("'$key' does not exist")
	}

	private fun <T> objMaybeNull(input: Any, key: String): T? {
		return (input as? JSONObject)?.let {
			val value = it[key]
			if (value == null) {
				null
			}
			else {
				@Suppress("UNCHECKED_CAST")
				(value as? T) ?: throw IOException("'$key' is of wrong type")
			}
		}
	}

	class Result(val content: StringBuilder?, val failure: Pair<IOException, Boolean>? = null)
}