package dev.aibtra.ai

import dev.aibtra.core.*
import org.json.simple.*
import org.json.simple.parser.*
import java.io.*
import java.net.*
import java.nio.charset.*

open class AIService(private val driver: AIDriver, private val apiToken: String?, private val debugLog: DebugLog) {

	protected fun request(model: String, params: String?, completionsEndpoint: String?, reasoningFilterName: String?, messages: JSONArray, handler: Handler, failureHandler: FailureHandler) {
		val input = JSONObject()
		driver.initializeInput(model, input)
		input["messages"] = messages

		try {
			params?.let {
				input.putAll(JSONParser().parse(it) as JSONObject)
			}
		} catch (ex: ParseException) {
			throw IOException("Invalid 'params' configuration", ex)
		}

		val reasoningFilter = driver.createReasoningFilter(reasoningFilterName)

		val streaming = handler is StreamingHandler
		input["stream"] = streaming

		val startTime = System.currentTimeMillis()

		val requestId = System.identityHashCode(messages)
		LOG.info("Sending request '$requestId' (model '$model')")
		val endpoint = completionsEndpoint?.let { URI.create(it) }
		val connection = driver.openConnection(endpoint, apiToken)
		try {
			connection.doOutput = true
			connection.requestMethod = "POST"
			connection.addRequestProperty("Content-Type", "application/json")

			debugLog.run("aiService", "network", DebugLog.Level.INFO) { log: DebugLog.Log, _: Boolean ->
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
									val rawBuilder = StringBuilder()
									val filteredBuilder = StringBuilder()
									while (true) {
										val line = reader.readLine() ?: break
										log.println(line)

										val finished = !driver.processStreamingLine(line, rawBuilder)
										reasoningFilter.process(rawBuilder, filteredBuilder, finished)
										if (finished) {
											break
										}

										if (!handler.process(filteredBuilder)) {
											break
										}
									}

									measureRequestTime(startTime, requestId)
									handler.finish(filteredBuilder)
								}
								is ResultHandler -> {
									InputStreamReader(input, StandardCharsets.UTF_8).use {
										val parser = JSONParser()
										val result = parser.parse(it) as? JSONObject ?: throw IOException("Invalid response (no JSON)")
										log.println(result.toString())

										val message = driver.processCompleteResponse(result)
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
						try {
							(parser.parse(InputStreamReader(errorStream, StandardCharsets.UTF_8)) as? JSONObject)?.let { result ->
								(result["error"] as? JSONObject)?.let { error ->
									(error["message"] as? String)?.let { message ->
										failureHandler.process(IOException("${ioe.message}:\n\n$message"), mightBeAuthentication)
									}
								}
							}
						} catch (ex: ParseException) {
							failureHandler.process(IOException("Failed to parse response: $ex", ex), mightBeAuthentication)
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

	sealed interface Handler

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

		fun createMessage(content: String, role: AIRole): JSONObject {
			val message = JSONObject()
			message["role"] = role.id
			message["content"] = content
			return message
		}
	}
}