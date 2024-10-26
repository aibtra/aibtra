package dev.aibtra.main.frame

import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.openai.OpenAIService
import dev.aibtra.text.FilteredText
import java.io.IOException

class OpenAIRequest(val profile: OpenAIConfiguration.Profile, private val service: OpenAIService, val failureCallback: (failure: IOException, mightBeAuthentication: Boolean) -> Unit) : RequestManager.Request {
	override fun run(filtered: FilteredText, callback: RequestManager.RequestCallback) {
		val part = filtered.clean
		val keywordResolver: (key: String) -> String? = { key ->
			when (key) {
				OpenAIConfiguration.CONTENT_KEYWORD -> {
					part.all
				}

				OpenAIConfiguration.SELECTION_KEYWORD -> {
					part.extract
				}

				else -> {
					null
				}
			}
		}

		// Whether the user opts to process the entire file or just a selection,
		// the responseType dictates whether we receive the whole file or just the selected portion.
		val responseType = profile.responseType
		val selectionMode = part.isPart()
		service.request(profile, selectionMode, true, keywordResolver) { result ->
			result.content?.let { builder ->
				val recreateMode = if (responseType == OpenAIConfiguration.ResponseType.SELECTION) {
					// If the user chooses to process the entire file, we will have passed the complete file to the model.
					// Therefore, our selection encompasses the whole file, making RecreateMode.PART identical to FULL.
					if (result.finished) {
						FilteredText.RecreateMode.PART
					}
					else {
						FilteredText.RecreateMode.PART_PREFIX
					}
				}
				else {
					FilteredText.RecreateMode.FULL
				}

				val res = filtered.recreate(builder.toString(), recreateMode)
				callback.callback(res)
			} ?: kotlin.run {
				val (failure, mightBeAuthentication) = requireNotNull(result.failure)
				failureCallback(failure, mightBeAuthentication)
				false
			}
		}
	}
}