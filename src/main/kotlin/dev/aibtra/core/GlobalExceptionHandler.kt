/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import java.text.*
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