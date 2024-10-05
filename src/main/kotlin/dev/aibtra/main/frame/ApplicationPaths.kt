/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import java.nio.file.Files
import java.nio.file.Path

class ApplicationPaths(
	val settingsPath: Path,
	val appName: String,
	private val propertyPrefix: String
) {
	fun getProperty(key: String): String? {
		return System.getProperty("${propertyPrefix}.$key")
	}

	companion object {
		fun initialize(appName: String, propertyPrefix: String): ApplicationPaths {
			val customSettingsPath = System.getProperty(propertyPrefix + ".settings")
			val settingsPath = if (customSettingsPath != null) {
				Path.of(customSettingsPath)
			}
			else {
				getDefaultSettingsPath(appName)
			}

			// Ensure to create the directory if it doesn't exist
			if (!Files.isDirectory(settingsPath)) {
				Files.createDirectories(settingsPath)
			}

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
	}
}