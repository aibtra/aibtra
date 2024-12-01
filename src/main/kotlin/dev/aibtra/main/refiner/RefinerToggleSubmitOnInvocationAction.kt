/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.main.content.*

class RefinerToggleSubmitOnInvocationAction(
	profileManager: RefinerProfileManager,
	accelerators: Accelerators
) :
	RefinerMainMenuProfileBooleanAction("toggleSubmitOnInvocation", "Submit on Invocation", null, null, null, accelerators,
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