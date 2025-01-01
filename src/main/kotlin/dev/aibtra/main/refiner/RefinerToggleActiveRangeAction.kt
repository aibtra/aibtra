/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.main.content.*
import dev.aibtra.refiner.*
import javax.swing.*

class RefinerToggleActiveRangeAction(
	private val diffManager: RefinerDiffManager,
	private val profileManager: RefinerProfileManager,
	rawEditor: RefinerRawEditor,
	accelerators: Accelerators
) :
	MainMenuAction("toggleActiveRange", "Transfer only active range", null, "Active Range", null, accelerators,
		{ action ->
			val raw = rawEditor.getText()
			val updated = if (action.isSelected()) {
				diffManager.updateRawText(raw, null).second
			}
			else {
				diffManager.updateRawText(raw, rawEditor.getActiveRange()).second
			}

			if (!updated) {
				(action as RefinerToggleActiveRangeAction).let {
					it.updateState()
					it.firePropertyChange(Action.SELECTED_KEY, true, false)
				}
			}
		}
	) {
	init {
		setSelectable(true)

		diffManager.addStateListener { _, _ -> updateState() }

		profileManager.addListener { _, _ -> updateState() }

		updateState()
	}

	private fun updateState() {
		val supportsSelection = profileManager.profile().supportsSelection()
		isEnabled = supportsSelection
		setSelected(diffManager.state.selection && supportsSelection)
	}
}
