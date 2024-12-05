package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.openai.*
import dev.aibtra.text.*

class RefinerOpenAIRequest(val profile: OpenAIRefinementConfiguration.Profile, private val service: OpenAIRefinementService, val retrieveCommand: () -> String, private val failureHandler: OpenAIService.FailureHandler) : RefinerRequestManager.Request {
	override fun run(filtered: FilteredText, callback: RefinerRequestManager.RequestCallback) {
		val part = filtered.clean
		val macroResolver = MacroResolver {
			when (it) {
				OpenAIRefinementConfiguration.CONTENT_MACRO -> part.all
				OpenAIRefinementConfiguration.SELECTION_MACRO -> part.extract
				OpenAIRefinementConfiguration.COMMAND_MACRO -> retrieveCommand()
				else -> null
			}
		}

		// Whether the user opts to process the entire file or just a selection,
		// the responseType dictates whether we receive the whole file or just the selected portion.
		val responseType = profile.responseType
		val selection = if (part.isPart()) OpenAIRefinementService.Selection(part.from) else null
		service.request(profile, selection, macroResolver) { result ->
			result.content?.let { builder ->
				val recreateMode = if (responseType == OpenAIRefinementConfiguration.ResponseType.SELECTION) {
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
				failureHandler.process(failure, mightBeAuthentication)
				false
			}
		}
	}
}