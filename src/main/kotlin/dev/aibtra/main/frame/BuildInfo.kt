/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.main.frame

import dev.aibtra.core.Logger
import dev.aibtra.gui.Ui
import dev.aibtra.main.startup.MainStartup
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class BuildInfo(val sha: String, val instant: String, val bundleType: BundleType?) {
	companion object {
		private val LOG = Logger.getLogger(this::class)

		fun load() : BuildInfo {
			val javaClass = MainStartup::class.java
			val jarPath = Path.of(javaClass.protectionDomain.codeSource.location.toURI())
			val bundleFile = jarPath.parent.resolve("aibtra.bundletype")

			val bundleType = if (Files.isRegularFile(bundleFile)) {
				val fileContent = Files.readString(bundleFile).trim()
				try {
					BundleType.valueOf(fileContent)
				} catch (e: IllegalArgumentException) {
					LOG.error("Invalid bundle type $fileContent")
					BundleType.stable
				}
			} else {
				if (Ui.isDebugging()) BundleType.experimental else BundleType.stable
			}

			LOG.info("Bundle type is $bundleType")

			javaClass.getResourceAsStream("/build.properties").use {
				val properties = Properties()
				if (it != null) {
					properties.load(it)
				}

				val sha = properties.getProperty("sha") ?: "<unknown>"
				val instant = properties.getProperty("time") ?: "unknown"
				return BuildInfo(sha, instant, bundleType)
			}
		}
	}

	enum class BundleType {
		stable, latest, experimental
	}
}