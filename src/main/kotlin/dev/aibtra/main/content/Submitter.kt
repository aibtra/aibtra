/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.content

import dev.aibtra.configuration.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.gui.dialogs.Panel
import dev.aibtra.ai.*
import java.awt.*
import javax.swing.*
import javax.swing.event.*

class Submitter(private val environment: Environment, private val dialogDisplayer: DialogDisplayer, private val submit: (apiToken: String?, failureHandler: RequestManagerFailureHandler) -> Unit) {
	fun submit(provider: AIProvider) {
		val configurationProvider = environment.configurationProvider
		val credentials = configurationProvider.get(AICredentials)
		if (credentials.providerToToken[provider] != null || !provider.driver.requiresToken()) {
			submit(provider, configurationProvider, dialogDisplayer)
		}
		else {
			OkCancelDialog("API Token") {
				val panel = Panel(3, 3)
				val width = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 16) 400 else 800

				val editorPane = JEditorPane("text/html", "<html><body style='width: $width'><b>Please provide an API token to access the ${provider.uiName} API.</b><br><br>It's recommended to create a dedicated token for this purpose to track usage. The token will be <b>stored in plaintext</b> in a configuration file on your local disk!<br><br>To create a token, follow <a href=\"${provider.apiKeyLink}\">this link</a>.</body></html>.")
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
					configurationProvider.change(AICredentials) {
						it.copy(providerToToken = it.providerToToken + (provider to token))
					}

					submit(provider, configurationProvider, dialogDisplayer)
				}
			}.show(dialogDisplayer)
		}
	}

	private fun submit(provider: AIProvider, configurationProvider: ConfigurationProvider, dialogDisplayer: DialogDisplayer) {
		val apiToken = configurationProvider.get(AICredentials).providerToToken[provider]
		require(apiToken != null || !provider.driver.requiresToken()) { "API token must not be null" }

		submit(apiToken) { failure, mightBeAuthentication ->
			if (mightBeAuthentication) {
				configurationProvider.change(AICredentials) {
					it.copy(providerToToken = it.providerToToken - provider)
				}
			}
			Dialogs.showIOError(failure, dialogDisplayer)
		}
	}
}
