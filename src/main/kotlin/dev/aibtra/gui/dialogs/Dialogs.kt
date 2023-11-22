/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import dev.aibtra.core.Logger
import java.awt.Window
import java.io.IOException
import java.util.function.Consumer
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

	fun showInfoDialog(title: String, message: String, dialogDisplayer: DialogDisplayer) {
		LOG.warn(message)

		showOptionPane(title, message, dialogDisplayer, JOptionPane.INFORMATION_MESSAGE, "OptionPane.infoIcon")
	}

	fun showConfirmationDialog(title: String, message: String, okText: String, dialogDisplayer: DialogDisplayer, okRunnable: Runnable) {
		LOG.warn(message)

		showOptionPane(title, message, dialogDisplayer, JOptionPane.INFORMATION_MESSAGE, "OptionPane.infoIcon", arrayOf(okText, "Cancel"), okText) {
			if (it == 0) {
				okRunnable.run()
			}
		}
	}

	private fun showOptionPane(title: String, message: String, dialogDisplayer: DialogDisplayer, messageType: Int, iconKey: String) {
		showOptionPane(title, message, dialogDisplayer, messageType, iconKey, null, null, null)
	}

	private fun showOptionPane(title: String, message: String, dialogDisplayer: DialogDisplayer, messageType: Int, iconKey: String, options: Array<String>?, defaultOption: String?, consumer: Consumer<Int>?) {
		dialogDisplayer.show { window: Window? ->
			val pane = JOptionPane(
				message, messageType,
				JOptionPane.DEFAULT_OPTION, UIManager.getIcon(iconKey),
				options, defaultOption
			)
			val dialog = pane.createDialog(window, title)
			dialog.isVisible = true
			if (consumer != null && options != null) {
				consumer.accept(options.indexOf(pane.value))
			}
		}
	}
}