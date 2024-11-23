/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

import dev.aibtra.main.frame.*
import dev.aibtra.main.startup.*

const val APPNAME = "Aibtra"

fun main(args: Array<String>) {
	val paths = ApplicationPaths.initialize(APPNAME, "aibtra")
	MainStartup.start(paths, args)
}
