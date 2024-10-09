/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

class ToggleSubmitOnProfileChangeAction(
	profileManager: ProfileManager,
	accelerators: Accelerators
) :
	MainMenuProfileBooleanAction("toggleSubmitOnProfileChange",
		"Submit on Profile Change",
		null,
		null,
		null,
		accelerators,
		profileManager,
		{ profile -> profile.submitOnProfileChange },
		{ profile, value ->
			profile.copy(submitOnProfileChange = value)
		},
		{
		}
	) {
	init {
		profileManager.addListener { _, _ ->
			updateState()
		}
	}
}