/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.gui.*
import kotlinx.coroutines.*
import java.util.*

interface Environment {
	val paths: ApplicationPaths
	val guiConfiguration: GuiConfiguration
	val accelerators: Accelerators
	val configurationProvider: ConfigurationProvider
	val theme: Theme
	val frameManager: FrameManager
	val buildInfo: BuildInfo
	val timer: Timer
	val coroutineDispatcher: CoroutineDispatcher
	val mainScope: CoroutineScope
	val systemTrayEnabled: Boolean
	val hotkeyListener: HotkeyListener
	val debugLog: DebugLog
}