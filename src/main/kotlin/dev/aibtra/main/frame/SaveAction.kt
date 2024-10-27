/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.gui.action.ActionRunnable

class SaveAction(workFile: WorkFile, environment: Environment) : MainMenuAction("save", "Save", "ctrl S", environment.accelerators, ActionRunnable {
	workFile.save() {}
}) {
	init {
		workFile.addStateListener { state ->
			updateEnabledState(state)
		}

		updateEnabledState(null)
	}

	private fun updateEnabledState(state: WorkFile.State?) {
		isEnabled = state != null && state.modified
	}
}