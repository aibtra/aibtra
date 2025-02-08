package dev.aibtra.ai

import org.json.simple.*
import java.net.*

interface AIDriver {
	fun initializeInput(model: String, input: JSONObject)

	fun openConnection(endpoint: URI?, apiToken: String?) : HttpURLConnection

	fun processCompleteResponse(result: JSONObject) : String

	fun processStreamingLine(line: String, builder: StringBuilder): Boolean

	fun requiresToken(): Boolean
}