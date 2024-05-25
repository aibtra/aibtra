/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui

import dev.aibtra.gui.action.DefaultAction
import javax.swing.JButton
import javax.swing.SwingUtilities

object Ui {
	fun assertEdt() {
		require(SwingUtilities.isEventDispatchThread())
	}

	fun runInEdt(runnable: Runnable) {
		SwingUtilities.invokeLater {
			runnable.run()
		}
	}

	fun sleepSafe(millis: Long) {
		try {
			Thread.sleep(millis)
		} catch (_: InterruptedException) {
		}
	}

	fun createButton(action: DefaultAction): JButton {
		require(!action.isSelectable())

		val button = JButton(action.title)
		button.addActionListener(action)
		return button
	}
}