/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.content

import java.nio.file.*
import java.util.*
import kotlin.io.path.*

class ApplicationPaths(
	val settingsPath: Path,
	val appName: String,
	private val propertyPrefix: String
) {
	fun getProperty(key: String): String? {
		return System.getProperty("${propertyPrefix}.$key")
	}

	companion object {
		fun initialize(appName: String, propertyPrefix: String, customSettingsPath: String?): ApplicationPaths {
			val settingsPath = if (customSettingsPath != null) {
				Path.of(customSettingsPath)
			}
			else {
				val propertySettingsPath = System.getProperty("$propertyPrefix.settings")
				if (propertySettingsPath != null) {
					Path.of(propertySettingsPath)
				}
				else {
					getDefaultSettingsPath(appName)
				}
			}

			// Ensure to create the directory if it doesn't exist
			if (!Files.isDirectory(settingsPath)) {
				Files.createDirectories(settingsPath)
			}

			loadSystemProperties(settingsPath.resolve("system.properties"))
			return ApplicationPaths(settingsPath, appName, propertyPrefix)
		}

		private fun getDefaultSettingsPath(appName: String): Path {
			val appNameLowerCase = appName.lowercase()
			val userHome = System.getProperty("user.home")
			val os = System.getProperty("os.name").lowercase()
			val settingsPath: String = when {
				os.contains("win") -> {
					val appData = System.getenv("APPDATA")
					"$appData\\$appName\\"
				}

				os.contains("mac") -> {
					"$userHome/Library/Application Support/$appName/"
				}

				os.contains("nix") || os.contains("nux") -> {
					"$userHome/.$appNameLowerCase/"
				}

				else -> {
					"$userHome/.$appNameLowerCase/"
				}
			}

			return Path.of(settingsPath)
		}

		private fun loadSystemProperties(path: Path) {
			if (path.exists()) {
				val properties = Properties().apply {
					path.inputStream().use { load(it) }
				}

				properties.forEach { (key, value) ->
					System.setProperty(key as String, value as String)
				}
			}
		}
	}
}