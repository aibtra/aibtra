package dev.aibtra.ai

import org.json.simple.*
import java.net.*

interface AIDriver {
	fun initializeInput(model: String, input: JSONObject)

	fun openConnection(endpoint: URI?, apiToken: String) : HttpURLConnection

	fun processCompleteResponse(result: JSONObject) : String

	fun processStreamingChunk(result: JSONObject): String?
}