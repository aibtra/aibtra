/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.action.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import java.io.*
import javax.swing.*

internal class RefinerOpenAction(tabbedPane: MainTabbedPane, environment: Environment, dialogDisplayer: DialogDisplayer) : MainMenuAction("open", "Open", "ctrl O", environment.accelerators, ActionRunnable {
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

	tabbedPane
		.filterIsInstance<RefinerFileTab>()
		.firstOrNull { it.getFile() == selectedPath }
		?.let {
			it.toFront()
			return@ActionRunnable
		}

	RefinerFileTab(tabbedPane, environment, dialogDisplayer).apply {
		setFile(selectedPath, null, null)
		tabbedPane.add(this)
	}
})
