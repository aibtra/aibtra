package dev.aibtra.ai

import dev.aibtra.core.*
import dev.aibtra.core.JsonUtils.Companion.objNotNull
import org.json.simple.*
import java.io.*
import java.net.*

class OpenAIDriver : AIDriver {

	override fun initializeInput(model: String, input: JSONObject) {
		input["model"] = model
		input["n"] = 1
	}

	override fun openConnection(endpoint: URI?, apiToken: String): HttpURLConnection {
		val uri = endpoint ?: URI("https://api.openai.com/v1/chat/completions")
		val connection = uri.toURL().openConnection() as HttpURLConnection
		connection.addRequestProperty("Authorization", "Bearer $apiToken")
		return connection
	}

	override fun processCompleteResponse(result: JSONObject): String {
		val choices = objNotNull<JSONArray>(result, "choices")
		if (choices.size != 1) {
			throw IOException("Unexpected number of 'choices'")
		}
		val choice = requireNotNull(choices[0])
		val messageOut = objNotNull<JSONObject>(choice, "message")
		return objNotNull(messageOut, "content")
	}

	override fun processStreamingChunk(result: JSONObject): String? {
		val choices = objNotNull<JSONArray>(result, "choices")
		if (choices.size != 1) {
			throw IOException("Unexpected number of 'choices'")
		}

		val choice = requireNotNull(choices[0])
		val messageOut = objNotNull<JSONObject>(choice, "delta")
		return if (JsonUtils.objMaybeNull<String>(choice, "finish_reason") == null) {
			objNotNull<String>(messageOut, "content")
		}
		else {
			null
		}
	}
}