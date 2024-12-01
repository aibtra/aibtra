/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.configuration.*
import dev.aibtra.diff.*
import dev.aibtra.main.content.*
import java.awt.*
import java.awt.datatransfer.*
import java.awt.event.*

internal class RefinerCopyAndCloseAction(
	tab: RefinerTextTab,
	environment: Environment,
	requestManager: RefinerRequestManager,
	diffManager: DiffManager,
	rawTextArea: RefinerRawTextArea,
	configurationProvider: ConfigurationProvider
) :
	MainMenuAction("copyAndClose", "Copy and Close", Icons.COPY, "Copy and Close", null, environment.accelerators, {
		val text = rawTextArea.getText()
		val selection = StringSelection(text)
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		clipboard.setContents(selection, selection)

		tab.initiateClose {
			if (configurationProvider.get(GuiConfiguration).pasteOnClose) {
				val robot = Robot()
				robot.keyPress(KeyEvent.VK_CONTROL)
				robot.keyPress(KeyEvent.VK_V)
				robot.keyRelease(KeyEvent.VK_V)
				robot.keyRelease(KeyEvent.VK_CONTROL)
			}
		}
	}) {

	init {
		fun updateEnabledState() {
			isEnabled = !requestManager.inProgress
		}

		requestManager.addProgressListener {
			updateEnabledState()
		}

		diffManager.addStateListener { _, _ ->
			updateEnabledState()
		}

		updateEnabledState()
	}
}


