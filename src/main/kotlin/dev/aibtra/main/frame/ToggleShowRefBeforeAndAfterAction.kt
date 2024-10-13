/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.diff.DiffManager

class ToggleShowRefBeforeAndAfterAction(
	diffManager: DiffManager,
	profileManager: ProfileManager,
	accelerators: Accelerators
) :
	MainMenuProfileBooleanAction("toggleShowRefBeforeAndAfter", "Show Diff Before/After", Icons.SHOW_REMOVED, "Before/After", null, accelerators,
		profileManager,
		{ profile -> profile.diffConfig.showRefBeforeAndAfter },
		{ profile, value ->
			profile.copy(diffConfig = profile.diffConfig.copy(showRefBeforeAndAfter = value))
		},
		{ profile ->
			diffManager.setConfig(profile.diffConfig)
		}
	) {
	init {
		profileManager.addListener { _, _ ->
			updateState()
		}
	}
}
