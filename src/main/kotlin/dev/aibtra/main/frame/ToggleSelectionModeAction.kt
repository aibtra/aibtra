/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.diff.DiffManager

class ToggleSelectionModeAction(
	private val diffManager: DiffManager,
	private val profileManager: ProfileManager,
	rawTextArea: RawTextArea,
	accelerators: Accelerators
) :
	MainMenuAction("toggleSelectionMode", "Transfer only Selection", null, "Selection Mode", null, accelerators,
		{ action ->
			val raw = rawTextArea.getText()
			if (action.isSelected()) {
				diffManager.updateRawText(raw, null)
			}
			else {
				diffManager.updateRawText(raw, rawTextArea.getSelectionRange())
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
