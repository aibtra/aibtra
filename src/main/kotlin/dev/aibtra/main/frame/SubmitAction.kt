/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.gui.Ui
import dev.aibtra.gui.action.ActionRunnable
import dev.aibtra.gui.dialogs.*
import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.openai.OpenAIService
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel

class SubmitAction(
	environment: Environment,
	requestManager: RequestManager,
	dialogDisplayer: DialogDisplayer,
	profile: () -> OpenAIConfiguration.Profile,
) :
	MainMenuAction("submit", "Submit", Icons.SUBMIT, "Submit", null, environment.accelerators, ActionRunnable { action -> (action as SubmitAction).worker.run() }) {

	private val worker: Worker = Worker(this, environment, requestManager, dialogDisplayer, profile)

	class Worker(private val action: SubmitAction, private val environment: Environment, private val requestManager: RequestManager, private val dialogDisplayer: DialogDisplayer, val profile: () -> OpenAIConfiguration.Profile) {
		private val stopMode = AtomicBoolean(false)

		init {
			requestManager.addProgressListener { inProgress ->
				stopMode.set(inProgress)
				action.toolBarIcon = if (inProgress) Icons.STOP else Icons.SUBMIT
			}
		}

		fun run() {
			if (stopMode.get()) {
				requestManager.stopCurrent()
				return
			}

			val configurationProvider = environment.configurationProvider
			val configuration = configurationProvider.get(OpenAIConfiguration)
			if (configuration.apiToken != null) {
				submit(configurationProvider, dialogDisplayer)
			}
			else {
				OkCancelDialog("API Token") {
					val panel = Panel(5, 3)
					val width = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 16) 400 else 800
					panel.add(JLabel("<html><body style='width: $width'>You have to provide an API token to access the OpenAI API. The token will be <b>stored in plaintext</b> in a configuration file on your local disk!</body></html>"), 0, 0, span = 3)

					val editor = Editor.password("Token")
					panel.add(editor, 2, 0)
					OkCancelDialog.Content(panel) {
						val token: String = editor.text
						configurationProvider.change(OpenAIConfiguration) {
							it.copy(apiToken = token)
						}

						submit(configurationProvider, dialogDisplayer)
					}
				}.show(dialogDisplayer)
			}
		}

		private fun submit(configurationProvider: ConfigurationProvider, dialogDisplayer: DialogDisplayer) {
			val apiToken = configurationProvider.get(OpenAIConfiguration).apiToken
			require(apiToken != null) { "API token must not be null" }

			val profile = profile()
			val service = OpenAIService(apiToken, environment.debugLog)
			requestManager.schedule { input: String, callback: RequestManager.OpCallback ->
				service.request(profile.model, profile.instructions, input, true) { result ->
					result.content?.let {
						callback.callback(it.toString())
					} ?: run {
						val (failure, mightBeAuthentication) = requireNotNull(result.failure)
						Ui.runInEdt {
							if (mightBeAuthentication) {
								configurationProvider.change(OpenAIConfiguration) {
									it.copy(apiToken = null)
								}
							}
							Dialogs.showIOError(failure, dialogDisplayer)
						}
						false
					}
				}
			}
		}
	}
}