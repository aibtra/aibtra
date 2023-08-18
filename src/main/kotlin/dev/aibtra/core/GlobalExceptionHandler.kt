/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs

class GlobalExceptionHandler {

	companion object {
		private val LOG = Logger.getLogger(this::class)

		fun install() {
			Thread.setDefaultUncaughtExceptionHandler { _, e -> handle(e) }
		}

		fun handle(th: Throwable) {
			LOG.error(th)

			Ui.runInEdt {
				Dialogs.showError("Internal Error", "An internal error has occurred. Please report at:\n\nhttps://github.com/aibtra/aibtra/issues", DialogDisplayer.createGlobal())
			}
		}
	}
}