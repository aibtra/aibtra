/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.gui.action.ActionRunnable
import dev.aibtra.gui.dialogs.DialogDisplayer
import java.io.File
import javax.swing.JFileChooser

internal class OpenAction(tabbedPane: MainTabbedPane, environment: Environment, dialogDisplayer: DialogDisplayer) : MainMenuAction("open", "Open", "ctrl O", environment.accelerators, ActionRunnable {
	val guiConfiguration = environment.configurationProvider.get(GuiConfiguration)
	val fileChooser = JFileChooser().apply {
		guiConfiguration.lastOpenPath?.let { currentDirectory = File(it) }
	}

	val result = fileChooser.showOpenDialog(tabbedPane.control)
	if (result != JFileChooser.APPROVE_OPTION) {
		return@ActionRunnable
	}

	val selectedPath = fileChooser.selectedFile?.toPath() ?: return@ActionRunnable
	environment.configurationProvider.change(GuiConfiguration) {
		it.copy(lastOpenPath = selectedPath.toString())
	}

	if (tabbedPane.iterate { tab ->
			(tab as? MainFileTab)?.let {
				if (it.getFile() == selectedPath) {
					it.toFront()
					true
				}
				else {
					null
				}
			}
		} == true) {
		return@ActionRunnable
	}

	MainFileTab(tabbedPane, environment, dialogDisplayer).apply {
		setFile(selectedPath, null, null)
		tabbedPane.add(this)
	}
})
