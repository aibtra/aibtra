package dev.aibtra.ai

import dev.aibtra.core.JsonUtils.Companion.objNotNull
import org.json.simple.*
import org.json.simple.parser.*
import java.io.*
import java.net.*

class OllamaDriver : AIDriver {

	override fun initializeInput(model: String, input: JSONObject) {
		input["model"] = model
	}

	override fun openConnection(endpoint: URI?, apiToken: String?): HttpURLConnection {
		endpoint?.let {
			return it.toURL().openConnection() as HttpURLConnection
		} ?: throw IOException("Missing 'endpoint' configuration")
	}

	override fun processCompleteResponse(result: JSONObject): String {
		val message = objNotNull<JSONObject>(result, "message")
		return objNotNull(message, "content")
	}

	override fun processStreamingLine(line: String, builder: StringBuilder): Boolean {
		return StringReader(line).use { reader ->
			val parser = JSONParser()
			val result = parser.parse(reader) as? JSONObject ?: throw IOException("Invalid response (no JSON)")
			val message = objNotNull<JSONObject>(result, "message")
			val content = objNotNull<String>(message, "content")
			builder.append(content)
			!objNotNull<Boolean>(result, "done")
		}
	}

	override fun requiresToken(): Boolean {
		return false
	}
}