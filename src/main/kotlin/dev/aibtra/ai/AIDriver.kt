package dev.aibtra.ai

import org.json.simple.*
import java.net.*

interface AIDriver {
	fun getCompletionsURI() : URI

	fun initializeInput(model: String, input: JSONObject)

	fun initializeConnection(connection: HttpURLConnection, apiToken: String)

	fun processCompleteResponse(result: JSONObject) : String

	fun processStreamingChunk(result: JSONObject): String?
}