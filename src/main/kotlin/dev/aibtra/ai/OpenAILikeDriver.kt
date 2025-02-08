package dev.aibtra.ai

import org.json.simple.*
import org.json.simple.parser.*
import java.io.*

abstract class OpenAILikeDriver : AIDriver {

	protected abstract fun processStreamingChunk(result: JSONObject): String?

	override fun processStreamingLine(line: String, builder: StringBuilder): Boolean {
		if (!line.startsWith("data: ")) {
			return true
		}

		val data = line.substring(6)
		if (data == "[DONE]") {
			return false
		}

		return StringReader(data).use { reader ->
			val parser = JSONParser()
			val result = parser.parse(reader) as? JSONObject ?: throw IOException("Invalid response (no JSON)")
			processStreamingChunk(result)?.let { chunk ->
				if (chunk.isNotEmpty()) {
					builder.append(chunk)
				}
				true
			} ?: false
		}
	}
}