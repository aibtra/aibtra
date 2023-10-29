/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationBooleanActionRunnable
import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.gui.Icon

open class MainMenuConfigurationBooleanAction<T>(
	id: String,
	title: String,
	toolBarIcon: Icon? = null,
	toolBarText: String? = null,
	keyStrokeDefault: String? = null,
	accelerators: Accelerators?,
	val configurationProvider: ConfigurationProvider,
	val configurationFactory: ConfigurationFactory<T>,
	val get: (T) -> Boolean,
	set: (T, Boolean) -> T,
	invoke: (T) -> Unit
) :
	MainMenuAction(
		id, title, toolBarIcon, toolBarText, keyStrokeDefault, accelerators,
		ConfigurationBooleanActionRunnable(configurationProvider, configurationFactory, get, set, invoke)
	) {

	init {
		setSelectable(true)

		updateState()
	}

	fun updateState() {
		setSelected(get(configurationProvider.get(configurationFactory)))
	}
}