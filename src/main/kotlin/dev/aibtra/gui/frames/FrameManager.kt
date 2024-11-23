/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */
@file:Suppress("UnusedImport")

package dev.aibtra.gui.frames

import dev.aibtra.gui.dialogs.*
import java.awt.*
import java.awt.event.*
import javax.swing.*

class FrameManager {
	private var frame: Frame? = null

	fun register(frame: Frame, jFrame: JFrame, exitOnClose: () -> Boolean) {
		this.frame = frame

		jFrame.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent?) {
				if (this@FrameManager.frame == frame) {
					this@FrameManager.frame = null

					frame.closed()
				}

				if (exitOnClose()) {
					exit()
				}
			}
		})
	}

	fun getFrame(): Frame? {
		return frame
	}

	fun close(finishRunnable: Runnable? = null) {
		val runnable = {
			this.frame?.closed()

			for (frame in java.awt.Frame.getFrames()) {
				frame.dispose()
			}

			finishRunnable?.run()
			Unit
		}

		frame?.checkClose(runnable) ?: runnable()
	}

	fun exit() {
		close {
			System.exit(0)
		}
	}

	interface Frame {
		val dialogDisplayer: DialogDisplayer

		fun checkClose(runnable: Runnable)

		fun closed()
	}
}