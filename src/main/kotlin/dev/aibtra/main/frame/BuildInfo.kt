/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.main.frame

import dev.aibtra.main.startup.MainStartup
import java.util.*

class BuildInfo(val sha: String, val instant: String) {
	companion object {
		fun load() : BuildInfo {
			MainStartup::class.java.getResourceAsStream("/build.properties").use {
				val properties = Properties()
				if (it != null) {
					properties.load(it)
				}

				val sha = properties.getProperty("sha") ?: "<unknown>"
				val instant = properties.getProperty("time") ?: "unknown"
				return BuildInfo(sha, instant)
			}
		}
	}
}