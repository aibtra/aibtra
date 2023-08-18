/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame

class FrameManager {
	private var frame: MainFrame? = null

	fun register(frame: MainFrame, jFrame: JFrame, exitOnClose: Boolean) {
		this.frame = frame

		jFrame.addWindowListener(object : WindowAdapter() {
			override fun windowClosed(e: WindowEvent?) {
				if (this@FrameManager.frame == frame) {
					this@FrameManager.frame = null

					frame.closed()
				}

				if (exitOnClose) {
					exit()
				}
			}
		})
	}

	fun getFrame(): MainFrame? {
		return frame
	}

	fun exit() {
		frame?.closed()

		for (frame in Frame.getFrames()) {
			frame.dispose()
		}

		System.exit(0)
	}
}