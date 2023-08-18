/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import dev.aibtra.core.Logger
import java.awt.Window
import java.io.IOException
import javax.swing.JOptionPane
import javax.swing.UIManager

object Dialogs {
	private val LOG = Logger.getLogger(Dialogs::class)

	fun showIOError(ex: IOException, dialogDisplayer: DialogDisplayer) {
		LOG.error(ex)

		showError("I/O Error", ex.message.orEmpty(), dialogDisplayer)
	}

	fun showError(title: String, message: String, dialogDisplayer: DialogDisplayer) {
		LOG.error(message)

		showOptionPane(title, message, dialogDisplayer, JOptionPane.WARNING_MESSAGE, "OptionPane.errorIcon")
	}

	fun showWarning(title: String, message: String, dialogDisplayer: DialogDisplayer) {
		LOG.warn(message)

		showOptionPane(title, message, dialogDisplayer, JOptionPane.WARNING_MESSAGE, "OptionPane.warningIcon")
	}

	private fun showOptionPane(title: String, message: String, dialogDisplayer: DialogDisplayer, messageType: Int, iconKey: String) {
		dialogDisplayer.show { window: Window? ->
			val pane = JOptionPane(
				message, messageType,
				JOptionPane.DEFAULT_OPTION, UIManager.getIcon(iconKey),
				null, null
			)
			val dialog = pane.createDialog(window, title)
			dialog.isVisible = true
		}
	}
}