/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.diff.DiffManager
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import javax.swing.JFrame

class CopyAndCloseAction(
	environment: Environment,
	requestManager: RequestManager,
	diffManager: DiffManager,
	rawTextArea: RawTextArea,
	configurationProvider: ConfigurationProvider,
	frame: JFrame
) :
	MainMenuAction("copyAndClose", "Copy and Close", Icons.COPY, "Copy and Close", null, environment.accelerators, {
		val text = rawTextArea.getText()
		val selection = StringSelection(text)
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		clipboard.setContents(selection, selection)
		frame.dispose()

		if (configurationProvider.get(GuiConfiguration).pasteOnClose) {
			val robot = Robot()
			robot.keyPress(KeyEvent.VK_CONTROL)
			robot.keyPress(KeyEvent.VK_V)
			robot.keyRelease(KeyEvent.VK_V)
			robot.keyRelease(KeyEvent.VK_CONTROL)
		}
	}) {

	init {
		fun updateEnabledState() {
			isEnabled = !requestManager.inProgress
		}

		requestManager.addProgressListener {
			updateEnabledState()
		}

		diffManager.addStateListener {
			updateEnabledState()
		}

		updateEnabledState()
	}
}


