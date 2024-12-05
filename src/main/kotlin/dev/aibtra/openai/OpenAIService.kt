package dev.aibtra.openai

import dev.aibtra.core.*
import dev.aibtra.core.JsonUtils.Companion.objNotNull
import org.json.simple.*
import org.json.simple.parser.*
import java.io.*
import java.net.*
import java.nio.charset.*

open class OpenAIService(private val apiToken: String, private val debugLog: DebugLog) {

	protected fun request(model: String, messages: JSONArray, handler: Handler, failureHandler: FailureHandler) {
		val input = JSONObject()
		input["model"] = model
		input["n"] = 1
		input["messages"] = messages

		val streaming = handler is StreamingHandler
		if (streaming) {
			input["stream"] = true
		}

		val url = URI("https://api.openai.com/v1/chat/completions").toURL()
		val connection = url.openConnection() as HttpURLConnection
		val startTime = System.currentTimeMillis()

		val requestId = System.identityHashCode(messages)
		LOG.info("Sending request '$requestId' (model '$model')")
		try {
			connection.doOutput = true
			connection.requestMethod = "POST"
			connection.addRequestProperty("Authorization", "Bearer $apiToken")
			connection.addRequestProperty("Content-Type", "application/json")

			debugLog.run("openai", "network", DebugLog.Level.INFO) { log: DebugLog.Log, _: Boolean ->
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
							when (handler) {
								is StreamingHandler -> {
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

											if (!parseDataChunk(data, builder, handler)) {
												break
											}
										}
									}

									measureRequestTime(startTime, requestId)
									handler.finish(builder)
								}
								is ResultHandler -> {
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

										measureRequestTime(startTime, requestId)
										handler.process(message)
									}
								}
							}
						}
					}
				} catch (ioe: IOException) {
					val parser = JSONParser()
					val mightBeAuthentication = connection.responseCode in AUTHENTICATION_RELATED_RESPONSE_CODES
					connection.errorStream?.let { errorStream ->
						(parser.parse(InputStreamReader(errorStream, StandardCharsets.UTF_8)) as? JSONObject)?.let { result ->
							(result["error"] as? JSONObject)?.let { error ->
								(error["message"] as? String)?.let { message ->
									failureHandler.process(IOException("${ioe.message}:\n\n$message"), mightBeAuthentication)
								}
							}
						}
					} ?: run {
						if (ioe is UnknownHostException) {
							failureHandler.process(IOException("Unknown host: ${ioe.message}"), false)
						}
						else {
							failureHandler.process(ioe, mightBeAuthentication)
						}
					}
				}
			}
		} finally {
			connection.disconnect()
		}
	}

	private fun measureRequestTime(startTime: Long, requestId: Int) {
		LOG.info("Finished request '$requestId' in ${System.currentTimeMillis() - startTime}ms")
	}

	private fun parseDataChunk(data: String, builder: StringBuilder, handler: StreamingHandler): Boolean {
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
				handler.process(builder)
			}
			else {
				false
			}
		}
	}

	sealed interface Handler {
	}

	interface StreamingHandler : Handler {
		fun process(builder: StringBuilder): Boolean

		fun finish(builder: StringBuilder)
	}

	interface ResultHandler : Handler {
		fun process(message: String)
	}

	interface FailureHandler {
		fun process(failure: IOException, mightBeAuthentication: Boolean)
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
		private val AUTHENTICATION_RELATED_RESPONSE_CODES = setOf(HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN)

		fun addMessage(content: String, role: OpenAIRole, array: JSONArray) {
			array.add(createMessage(content, role))
		}

		fun createMessage(content: String, role: OpenAIRole): JSONObject {
			val message = JSONObject()
			message["role"] = role.id
			message["content"] = content
			return message
		}
	}
}