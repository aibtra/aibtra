/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.action.*
import dev.aibtra.main.content.*

class ResolverSaveAction(private val resolverSaver: ResolverSaver, environment: Environment) : MainMenuAction("save", "Save", Icons.SAVE, "Save", "ctrl S", environment.accelerators, ActionRunnable {
	resolverSaver.save {
	}
}) {
	init {
		resolverSaver.addStateListener {
			updateEnabledState()
		}

		updateEnabledState()
	}

	private fun updateEnabledState() {
		isEnabled = resolverSaver.isModified()
	}
}