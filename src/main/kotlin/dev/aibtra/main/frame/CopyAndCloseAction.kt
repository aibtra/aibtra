/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.diff.DiffManager
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JFrame

class CopyAndCloseAction(
	environment: Environment,
	requestManager: RequestManager,
	diffManager: DiffManager,
	rawTextArea: RawTextArea,
	frame: JFrame
) :
	MainMenuAction("copyAndClose", "Copy and Close", Icons.COPY, "Copy and Close", null, environment.accelerators, {
		val text = rawTextArea.getText()
		val selection = StringSelection(text)
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		clipboard.setContents(selection, selection)
		frame.dispose()
	}) {

	init {
		fun updateEnabledState() {
			isEnabled = !requestManager.inProgress
		}

		requestManager.addProgressListener {
			updateEnabledState()
		}

		diffManager.addListener {
			updateEnabledState()
		}

		updateEnabledState()
	}
}


