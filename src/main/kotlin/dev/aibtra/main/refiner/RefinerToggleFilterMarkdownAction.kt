/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.diff.*
import dev.aibtra.main.content.*
import dev.aibtra.refiner.*

class RefinerToggleFilterMarkdownAction(
	diffManager: RefinerDiffManager,
	profileManager: RefinerProfileManager,
	accelerators: Accelerators
) :
	RefinerMainMenuProfileBooleanAction("toggleFilterMarkdown", "Filter Markdown", null, null, null, accelerators,
		profileManager,
		{ profile -> profile.diffConfig.filterMarkdown },
		{ profile, value ->
			profile.copy(diffConfig = profile.diffConfig.copy(filterMarkdown = value))
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
