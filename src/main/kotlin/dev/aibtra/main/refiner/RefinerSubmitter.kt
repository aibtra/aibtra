/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.openai.*

class RefinerSubmitter(private val environment: Environment, private val requestManager: RefinerRequestManager, private val commandControl: RefinerCommandControl, private val dialogDisplayer: DialogDisplayer, val profile: () -> OpenAIRefinementConfiguration.Profile) {
	init {
		commandControl.registerEnterListener { ev ->
			run()
			ev.consume()
		}
	}

	fun run() {
		Submitter(environment, dialogDisplayer) { apiToken, failureHandler ->
			val profile = profile()
			val service = OpenAIRefinementService(apiToken, environment.debugLog)
			val request = RefinerOpenAIRequest(profile, service, { commandControl.retrieveCommand() }, failureHandler)
			requestManager.schedule(request)
		}.submit()
	}
}
