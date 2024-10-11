/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

class ToggleWordWrapAction(
	rawTextArea: RawTextArea,
	refTextArea: RefTextArea,
	profileManager: ProfileManager,
	accelerators: Accelerators
) :
	MainMenuProfileBooleanAction("toggleLineWrap", "Word Wrap", null, null, null, accelerators,
		profileManager,
		{ profile -> profile.wordWrap },
		{ profile, value ->
			profile.copy(wordWrap = value)
		},
		{ profile ->
			val wordWrap = profile.wordWrap
			rawTextArea.setWordWrap(wordWrap)
			refTextArea.setWordWrap(wordWrap)
		}
	) {
	init {
		profileManager.addListener { _, _ ->
			updateState()
		}
	}
}