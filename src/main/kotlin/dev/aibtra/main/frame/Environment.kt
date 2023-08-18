/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import kotlinx.coroutines.CoroutineDispatcher
import java.util.*

interface Environment {
	val paths: ApplicationPaths
	val guiConfiguration: GuiConfiguration
	val accelerators: Accelerators
	val configurationProvider: ConfigurationProvider
	val theme: Theme
	val frameManager: FrameManager
	val timer: Timer
	val coroutineDispatcher: CoroutineDispatcher
	val systemTrayEnabled: Boolean
}