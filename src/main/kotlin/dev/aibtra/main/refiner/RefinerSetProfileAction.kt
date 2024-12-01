/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.main.content.*
import dev.aibtra.openai.*
import javax.swing.*

class RefinerSetProfileAction(
	profile: OpenAIProfile.Name,
	profileManager: RefinerProfileManager,
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