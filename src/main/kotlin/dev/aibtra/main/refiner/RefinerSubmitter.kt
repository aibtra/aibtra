/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.ai.*
import dev.aibtra.core.*

class RefinerSubmitter(private val environment: Environment, private val requestManager: RefinerRequestManager, private val commandControl: RefinerCommandControl, private val dialogDisplayer: DialogDisplayer, val profile: () -> AIRefinementConfiguration.Profile) {
	init {
		commandControl.registerEnterListener { ev ->
			run()
			ev.consume()
		}
	}

	fun run() {
		LOG.debug("run")

		val profile = profile()
		Submitter(environment, dialogDisplayer) { apiToken, failureHandler ->
			LOG.debug("submit")

			val service = AIRefinementService(profile.provider.driver, apiToken, environment.debugLog)
			val request = RefinerAIRequest(profile, service) { commandControl.retrieveCommand() }
			requestManager.schedule(request, failureHandler)
		}.submit(profile.provider)
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}
