/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.main.content.*

class RefinerToggleWordWrapAction(
	rawEditor: RefinerRawEditor,
	refEditor: RefinerRefEditor,
	profileManager: RefinerProfileManager,
	accelerators: Accelerators
) :
	RefinerMainMenuProfileBooleanAction("toggleLineWrap", "Word Wrap", null, null, null, accelerators,
		profileManager,
		{ profile -> profile.wordWrap },
		{ profile, value ->
			profile.copy(wordWrap = value)
		},
		{ profile ->
			val wordWrap = profile.wordWrap
			rawEditor.setWordWrap(wordWrap)
			refEditor.setWordWrap(wordWrap)
		}
	) {
	init {
		profileManager.addListener { _, _ ->
			updateState()
		}
	}
}