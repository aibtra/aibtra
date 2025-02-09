package dev.aibtra.ai

import dev.aibtra.core.*
import dev.aibtra.core.JsonUtils.Companion.objNotNull
import org.json.simple.*
import java.io.*
import java.net.*

class DeepSeekDriver : OpenAILikeDriver() {

	override fun initializeInput(model: String, input: JSONObject) {
		input["model"] = model
		input["n"] = 1
	}

	override fun createReasoningFilter(filterName: String?): AIReasoningFilter {
		DeepSeekDriver.createReasoningFilter(filterName)?.let {
			return it
		}

		return super.createReasoningFilter(filterName)
	}

	override fun openConnection(endpoint: URI?, apiToken: String?): HttpURLConnection {
		val uri = endpoint ?: URI("https://api.deepseek.com/chat/completions")
		val connection = uri.toURL().openConnection() as HttpURLConnection
		apiToken.let { connection.addRequestProperty("Authorization", "Bearer $it") }
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

	override fun requiresToken(): Boolean {
		return true
	}

	private class ThinkTagFilter : AIReasoningFilter {
		private var lastRawLength = 0
		private var lastFilteredLength = 0

		override fun process(raw: StringBuilder, filtered: StringBuilder, finished: Boolean): Boolean {
			require(lastRawLength <= raw.length)
			require(lastFilteredLength <= filtered.length)

			val lastRawLength = this.lastRawLength
			this.lastRawLength = raw.length
			this.lastFilteredLength = filtered.length

			if (!raw.contains("\n") && !finished) {
				// Wait for the full first line, so our below checks will work
				return false
			}

			if (filtered.isNotEmpty() || !raw.startsWith(THINK_START)) {
				filtered.append(raw.substring(lastRawLength))
				return true
			}

			val stop = raw.indexOf(THINK_END)
			if (stop == -1) {
				return false
			}
			filtered.append(raw.substring(stop + THINK_END.length))
			return true
		}
	}

	companion object {
		private const val THINK_START = "<think>\n"
		private const val THINK_END = "\n</think>\n\n"

		fun createReasoningFilter(filterName: String?): AIReasoningFilter? {
			if (filterName == "think") {
				return ThinkTagFilter()
			}

			return null
		}
	}
}