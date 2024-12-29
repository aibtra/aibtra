/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.*
import dev.aibtra.main.content.*
import dev.aibtra.ai.*

open class RefinerMainMenuProfileBooleanAction(
	id: String,
	title: String,
	toolBarIcon: Icon? = null,
	toolBarText: String? = null,
	keyStrokeDefault: String? = null,
	accelerators: Accelerators?,
	private val profileManager: RefinerProfileManager,
	val get: (AIRefinementConfiguration.Profile) -> Boolean,
	set: (AIRefinementConfiguration.Profile, Boolean) -> AIRefinementConfiguration.Profile,
	invoke: (AIRefinementConfiguration.Profile) -> Unit
) :
	MainMenuAction(
		id, title, toolBarIcon, toolBarText, keyStrokeDefault, accelerators,
		{ action ->
			val profile = profileManager.updateCurrentProfile { profile ->
				val oldValue = get(profile)
				set(profile, !oldValue)
			}

			val newValue = get(profile) // maybe set does not actually flip the value
			action.setSelected(newValue)
			invoke(profile)
		}
	) {

	init {
		setSelectable(true)

		updateState()
	}

	fun updateState() {
		setSelected(get(profileManager.profile()))
	}
}