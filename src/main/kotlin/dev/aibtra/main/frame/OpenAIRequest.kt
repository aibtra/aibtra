package dev.aibtra.main.frame

import dev.aibtra.openai.OpenAIProfiles
import dev.aibtra.openai.OpenAIService
import dev.aibtra.text.FilteredText
import java.io.IOException

class OpenAIRequest(val profile: OpenAIProfiles.Profile, private val service: OpenAIService, val retrieveCommand : () -> String, val failureCallback: (failure: IOException, mightBeAuthentication: Boolean) -> Unit) : RequestManager.Request {
	override fun run(filtered: FilteredText, callback: RequestManager.RequestCallback) {
		val part = filtered.clean
		val keywordResolver: (key: String) -> String? = { key ->
			when (key) {
				OpenAIProfiles.CONTENT_KEYWORD -> {
					part.all
				}

				OpenAIProfiles.SELECTION_KEYWORD -> {
					part.extract
				}

				OpenAIProfiles.COMMAND_KEYWORD -> {
					retrieveCommand()
				}

				else -> {
					null
				}
			}
		}

		// Whether the user opts to process the entire file or just a selection,
		// the responseType dictates whether we receive the whole file or just the selected portion.
		val responseType = profile.responseType
		val selection = if (part.isPart()) OpenAIService.Selection(part.from) else null
		service.request(profile, selection, keywordResolver) { result ->
			result.content?.let { builder ->
				val recreateMode = if (responseType == OpenAIProfiles.ResponseType.SELECTION) {
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