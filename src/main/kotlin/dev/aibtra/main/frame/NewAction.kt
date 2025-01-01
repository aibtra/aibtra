/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.WorkingMode
import dev.aibtra.gui.action.ActionRunnable
import dev.aibtra.gui.dialogs.DialogDisplayer
import java.io.File
import javax.swing.JFileChooser

internal class NewAction(tabbedPane: MainTabbedPane, environment: Environment, dialogDisplayer: DialogDisplayer) : MainMenuAction("new", "New", "ctrl N", environment.accelerators, ActionRunnable {
	val tab = MainTextTab(WorkingMode.OPEN, tabbedPane, environment, dialogDisplayer)
	tabbedPane.add(tab)
	tab.requestFocus()
})
