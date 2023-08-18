/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.startup

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.main.frame.*
import kotlinx.coroutines.CoroutineDispatcher
import java.util.*

class MainApplication(
	override val paths: ApplicationPaths,
	override val configurationProvider: ConfigurationProvider,
	override val coroutineDispatcher: CoroutineDispatcher,
	override val systemTrayEnabled: Boolean
) : Environment {
	override val guiConfiguration: GuiConfiguration = configurationProvider.get(GuiConfiguration)
	override val accelerators: Accelerators = configurationProvider.get(Accelerators)
	override val timer = Timer(true)
	override val frameManager = FrameManager()
	override val theme = Theme(configurationProvider)
}