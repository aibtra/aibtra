/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.configuration

import dev.aibtra.gui.action.ActionRunnable
import dev.aibtra.gui.action.DefaultAction

class ConfigurationBooleanActionRunnable<T>(
	private val configurationProvider: ConfigurationProvider,
	private val configurationFactory: ConfigurationFactory<T>,
	private val get: (T) -> Boolean,
	private val set: (T, Boolean) -> T,
	private val invoke: (T) -> Unit
) : ActionRunnable {
	override fun run(action: DefaultAction) {
		configurationProvider.change(configurationFactory) {
			val oldValue = get(it)
			set(it, !oldValue)
		}

		val newConfig = configurationProvider.get(configurationFactory)
		val newValue = get(newConfig) // maybe set does not actually flip the value
		action.setSelected(newValue)
		invoke(newConfig)
	}
}