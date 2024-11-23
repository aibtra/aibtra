/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.configuration

import dev.aibtra.gui.action.*

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
		invoke(newConfig)

		val finalConfig = configurationProvider.get(configurationFactory)
		val finalValue = get(finalConfig) // maybe set does not actually flip the value or invoke() reverts the value
		action.setSelected(finalValue)
	}
}