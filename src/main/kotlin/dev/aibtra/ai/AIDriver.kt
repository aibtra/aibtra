package dev.aibtra.ai

import org.json.simple.*
import java.io.IOException
import java.net.*

interface AIDriver {
	fun initializeInput(model: String, input: JSONObject)

	fun createReasoningFilter(filterName: String?) : AIReasoningFilter {
		if (filterName != null) {
			throw IOException("Unsupported response filter '$filterName'")
		}

		return AIReasoningFilter.NoFilter()
	}

	fun openConnection(endpoint: URI?, apiToken: String?) : HttpURLConnection

	fun processCompleteResponse(result: JSONObject) : String

	fun processStreamingLine(line: String, builder: StringBuilder): Boolean

	fun requiresToken(): Boolean
}