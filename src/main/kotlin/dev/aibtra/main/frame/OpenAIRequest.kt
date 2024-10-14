package dev.aibtra.main.frame

import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.openai.OpenAIService
import dev.aibtra.text.FilteredText
import java.io.IOException

class OpenAIRequest(val profile: OpenAIConfiguration.Profile, private val service: OpenAIService, val failureCallback: (failure: IOException, mightBeAuthentication: Boolean) -> Unit) : RequestManager.Request {
	override fun run(filtered: FilteredText, callback: RequestManager.RequestCallback) {
		val input = filtered.clean
		val keywordResolver: (key: String) -> String? = { key ->
			if (key == OpenAIConfiguration.CONTENT_KEYWORD) {
				input
			}
			else {
				null
			}
		}

		service.request(profile, true, keywordResolver) { result ->
			result.content?.let { builder ->
				val res = filtered.recreate(builder.toString())
				callback.callback(res)
			} ?: kotlin.run {
				val (failure, mightBeAuthentication) = requireNotNull(result.failure)
				failureCallback(failure, mightBeAuthentication)
				false
			}
		}
	}
}