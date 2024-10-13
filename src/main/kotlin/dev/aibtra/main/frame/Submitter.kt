/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.*
import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.openai.OpenAIService
import javax.swing.JLabel

class Submitter(private val environment: Environment, private val requestManager: RequestManager, private val dialogDisplayer: DialogDisplayer, val profile: () -> OpenAIConfiguration.Profile) {
	fun run() {
		val configurationProvider = environment.configurationProvider
		val configuration = configurationProvider.get(OpenAIConfiguration)
		if (configuration.apiToken != null) {
			submit(configurationProvider, dialogDisplayer)
		}
		else {
			OkCancelDialog("API Token") {
				val panel = Panel(5, 3)
				val width = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 16) 400 else 800
				panel.add(JLabel("<html><body style='width: $width'>Please provide an API token to access the OpenAI API.<br><br>It's recommended to create a dedicated token for this purpose to track usage. The token will be <b>stored in plaintext</b> in a configuration file on your local disk!</body></html>"), 0, 0, span = 3)

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
			val keywordResolved: (key: String) -> String? = { key ->
				if (key == OpenAIConfiguration.CONTENT_KEYWORD) {
					input
				}
				else {
					null
				}
			}

			service.request(profile, true, keywordResolved) { result ->
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
