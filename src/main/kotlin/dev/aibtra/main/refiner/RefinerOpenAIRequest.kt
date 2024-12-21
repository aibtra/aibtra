package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.main.content.*
import dev.aibtra.openai.*
import dev.aibtra.refiner.*
import dev.aibtra.text.*

class RefinerOpenAIRequest(val profile: OpenAIRefinementConfiguration.Profile, private val service: OpenAIRefinementService, val retrieveCommand: () -> String) : RefinerRequestManager.Request {
	override fun run(filtered: FilteredText, priorConversation: RefinerConversation?, callback: RefinerRequestManager.RequestCallback, failureHandler: RequestManagerFailureHandler) {
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
		service.request(profile, part, priorConversation, macroResolver) { result ->
			result.content?.let { builder ->
				val conversation = result.conversation
				val recreateMode = if (part.isPart()) {
					// If the user chooses to process the entire file, we will have passed the complete file to the model.
					// Therefore, our selection encompasses the whole file, making RecreateMode.PART identical to FULL.
					if (conversation != null) {
						FilteredText.RecreateMode.PART
					}
					else {
						FilteredText.RecreateMode.PART_PREFIX
					}
				}
				else {
					FilteredText.RecreateMode.FULL
				}

				val res = filtered.recreate(builder, recreateMode)
				callback.callback(res, conversation)
			} ?: kotlin.run {
				val (failure, mightBeAuthentication) = requireNotNull(result.failure)
				failureHandler.process(failure, mightBeAuthentication)
				false
			}
		}
	}
}