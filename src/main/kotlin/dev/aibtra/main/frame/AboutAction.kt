/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.Logger
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import java.awt.Desktop
import java.net.URI

class AboutAction(
	environment: Environment,
	dialogDisplayer: DialogDisplayer
) : MainMenuAction("about", "About", null, environment.accelerators, {
	openUrlInBrowser("https://www.aibtra.dev", dialogDisplayer)
}) {
	companion object {
		private val LOG = Logger.getLogger(this::class)

		fun openUrlInBrowser(url: String, dialogDisplayer: DialogDisplayer) {
			try {
				Desktop.getDesktop().browse(URI.create(url))
			} catch (ex: Exception) {
				LOG.error(ex)

				Dialogs.showError("Open Browser", "Failed to open your web browser", dialogDisplayer)
			}
		}
	}
}