/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import java.awt.Window
import java.util.function.Consumer
import javax.swing.JDialog
import javax.swing.JFrame

interface DialogDisplayer {
	fun show(windowConsumer: Consumer<Window?>)

	companion object {
		fun createGlobal(): DialogDisplayer {
			return object : DialogDisplayer {
				override fun show(windowConsumer: Consumer<Window?>) {
					windowConsumer.accept(null)
				}
			}
		}

		fun create(frame: JFrame): DialogDisplayer {
			return object : DialogDisplayer {
				override fun show(windowConsumer: Consumer<Window?>) {
					windowConsumer.accept(frame)
				}
			}
		}

		fun create(dialog: JDialog): DialogDisplayer {
			return object : DialogDisplayer {
				override fun show(windowConsumer: Consumer<Window?>) {
					windowConsumer.accept(dialog)
				}
			}
		}
	}
}