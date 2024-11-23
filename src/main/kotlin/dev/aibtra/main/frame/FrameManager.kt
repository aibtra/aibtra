/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import java.awt.*
import java.awt.event.*
import javax.swing.*

class FrameManager {
	private var frame: MainFrame? = null

	fun register(frame: MainFrame, jFrame: JFrame, exitOnClose: () -> Boolean) {
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

	fun getFrame(): MainFrame? {
		return frame
	}

	fun close(finishRunnable: Runnable? = null) {
		val runnable = {
			this.frame?.closed()

			for (frame in Frame.getFrames()) {
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
}