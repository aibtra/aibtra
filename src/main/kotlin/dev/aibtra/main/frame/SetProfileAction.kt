/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.openai.*
import javax.swing.*

class SetProfileAction(
	profile: OpenAIProfile.Name,
	profileManager: ProfileManager,
	radioButtonGroup: ButtonGroup
) : MainMenuAction("profile-" + profile.id, profile.title, profileManager.getProfile(profile)?.accelerator, null, {
	profileManager.setProfile(profile)
}) {
	init {
		setRadioButtonGroup(radioButtonGroup)

		setSelected(profile == profileManager.profile().name)
		profileManager.addListener { _, name ->
			setSelected(profile == name)
		}
	}
}