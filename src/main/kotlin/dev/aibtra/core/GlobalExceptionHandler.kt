/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class GlobalExceptionHandler {

	companion object {
		private val LOG = Logger.getLogger(this::class)
		private val DATE_FORMAT: DateFormat = SimpleDateFormat("yyyyMMddHHmmss")

		fun install() {
			Thread.setDefaultUncaughtExceptionHandler { _, e -> handle(e) }
		}

		fun handle(th: Throwable) {
			LOG.error(th)

			Logger.backup("bug-" + DATE_FORMAT.format(Date()))

			Ui.runInEdt {
				Dialogs.showError("Internal Error", "An internal error has occurred. Please report at:\n\nhttps://github.com/aibtra/aibtra/issues", DialogDisplayer.createGlobal())
			}
		}
	}
}