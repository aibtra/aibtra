/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.configuration.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.gui.dialogs.Panel
import dev.aibtra.main.content.*
import dev.aibtra.openai.*
import java.awt.*
import javax.swing.*
import javax.swing.event.*

class RefinerSubmitter(private val environment: Environment, private val requestManager: RefinerRequestManager, private val commandControl: RefinerCommandControl, private val dialogDisplayer: DialogDisplayer, val profile: () -> OpenAIRefinementConfiguration.Profile) {
	init {
		commandControl.registerEnterListener { ev ->
			run()
			ev.consume()
		}
	}

	fun run() {
		val configurationProvider = environment.configurationProvider
		val credentials = configurationProvider.get(OpenAICredentials)
		if (credentials.apiToken != null) {
			submit(configurationProvider, dialogDisplayer)
		}
		else {
			OkCancelDialog("API Token") {
				val panel = Panel(3, 3)
				val width = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 16) 400 else 800

				val editorPane = JEditorPane("text/html", "<html><body style='width: $width'><b>Please provide an API token to access the OpenAI API.</b><br><br>It's recommended to create a dedicated token for this purpose to track usage. The token will be <b>stored in plaintext</b> in a configuration file on your local disk!<br><br>To create a token, follow <a href=\"https://platform.openai.com/settings/organization/api-keys\">this link</a>.</body></html>.")
				editorPane.isEditable = false
				editorPane.margin = null
				editorPane.border = null
				editorPane.isFocusable = false
				editorPane.addHyperlinkListener { e ->
					if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
						if (Desktop.isDesktopSupported()) {
							Desktop.getDesktop().browse(e.url.toURI())
						}
					}
				}

				var row = 0
				panel.add(editorPane, row++, 0, span = 3)
				panel.addSeparatorRow(row++)

				val editor = Editor.password("Token")
				panel.add(editor, row, 0)

				OkCancelDialog.Content(panel) {
					val token: String = editor.text
					configurationProvider.change(OpenAICredentials) {
						it.copy(apiToken = token)
					}

					submit(configurationProvider, dialogDisplayer)
				}
			}.show(dialogDisplayer)
		}
	}

	private fun submit(configurationProvider: ConfigurationProvider, dialogDisplayer: DialogDisplayer) {
		val apiToken = configurationProvider.get(OpenAICredentials).apiToken
		require(apiToken != null) { "API token must not be null" }

		val profile = profile()
		val service = OpenAIRefinementService(apiToken, environment.debugLog)
		val request = RefinerOpenAIRequest(profile, service, { commandControl.retrieveCommand() }) { failure, mightBeAuthentication ->
			Ui.runInEdt {
				if (mightBeAuthentication) {
					configurationProvider.change(OpenAICredentials) {
						it.copy(apiToken = null)
					}
				}
				Dialogs.showIOError(failure, dialogDisplayer)
			}
		}
		requestManager.schedule(request)
	}
}
