/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

import dev.aibtra.main.frame.ApplicationPaths
import dev.aibtra.main.startup.MainStartup
import java.nio.file.Path

const val APPNAME = "Aibtra"

fun main(args: Array<String>) {
	val appNameLowerCase = APPNAME.lowercase()
	val paths = System.getProperty("$appNameLowerCase.settings")?.let {
		ApplicationPaths(Path.of(it), APPNAME)
	} ?: ApplicationPaths.initializeDefaultSettingsPath(APPNAME)

	MainStartup.start(paths, args)
}
