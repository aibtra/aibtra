/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.gui.action.ActionRunnable
import java.awt.Component
import javax.swing.JFileChooser

class OpenAction(workFile: WorkFile, parent: Component, environment: Environment) : MainMenuAction("open", "Open", "ctrl O", environment.accelerators, ActionRunnable {
	workFile.checkSave {
		val fileChooser = JFileChooser()
		workFile.state?.let {
			fileChooser.currentDirectory = it.path.parent.toFile()
		}

		val result = fileChooser.showOpenDialog(parent)
		if (result != JFileChooser.APPROVE_OPTION) {
			return@checkSave
		}

		val selectedFile = fileChooser.selectedFile
		if (selectedFile == null) {
			return@checkSave
		}

		workFile.load(selectedFile.toPath())
	}
})