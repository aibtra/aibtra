/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

class ToggleSubmitOnInvocationAction(
	profileManager: ProfileManager,
	accelerators: Accelerators
) :
	MainMenuProfileBooleanAction("toggleSubmitOnInvocation", "Submit on Invocation", null, null, null, accelerators,
		profileManager,
		{ profile -> profile.submitOnInvocation },
		{ profile, value ->
			profile.copy(submitOnInvocation = value)
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