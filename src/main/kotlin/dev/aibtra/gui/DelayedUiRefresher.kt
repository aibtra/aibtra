/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui

import javax.swing.Timer

class DelayedUiRefresher(
	private val delay: Int,
	val run: () -> Unit
) {

	private var timer: Timer? = null

	fun refresh() {
		Ui.assertEdt()

		timer?.let {
			if (!it.isRunning) {
				it.restart()
			}
		} ?: run {
			val timer = Timer(delay) {
				run()
			}

			this.timer = timer
			timer.start()
		}
	}
}