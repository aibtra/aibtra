package dev.aibtra.ai

import dev.aibtra.core.JsonUtils.Companion.objNotNull
import org.json.simple.*
import java.io.*
import java.net.*

class AnthropicDriver : AIDriver {

	override fun getCompletionsURI() : URI {
		return URI("https://api.anthropic.com/v1/messages")
	}

	override fun initializeInput(model: String, input: JSONObject) {
		input["model"] = model
		input["max_tokens"] = 8192 // Error message "max_tokens: 2147483647 > 8192, which is the maximum allowed number of output tokens for claude-3-5-sonnet-20241022"
	}

	override fun initializeConnection(connection: HttpURLConnection, apiToken: String) {
		connection.addRequestProperty("x-api-key", apiToken)
		connection.addRequestProperty("anthropic-version", "2023-06-01")
	}

	override fun processCompleteResponse(result: JSONObject): String {
		val content = objNotNull<JSONArray>(result, "content")
		if (content.size != 1) {
			throw IOException("Unexpected number of 'content'")
		}

		val first = requireNotNull(content[0])
		val type = objNotNull<String>(first, "type")
		if (type != "text") {
			throw IOException("Unexpected type '$type'")
		}

		return objNotNull(first, "text")
	}

	override fun processStreamingChunk(result: JSONObject): String? {
		val type = objNotNull<String>(result, "type")
		if (type == "message_start" ||
			type == "content_block_start" ||
			type == "ping") {
			return ""
		}
		if (type == "content_block_stop") {
			return null
		}

		val delta = objNotNull<JSONObject>(result, "delta")
		return objNotNull<String>(delta, "text")
	}
}