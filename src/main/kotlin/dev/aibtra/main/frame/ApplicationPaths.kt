/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import java.nio.file.Files
import java.nio.file.Path

class ApplicationPaths(
	val settingsPath: Path,
	val appName: String
) {
	fun getProperty(key: String): String? {
		return System.getProperty("${appName.lowercase()}.$key")
	}

	companion object {
		fun initializeDefaultSettingsPath(appName: String): ApplicationPaths {
			val appNameLowerCase = appName.lowercase()
			val userHome = System.getProperty("user.home")
			val os = System.getProperty("os.name").lowercase()
			val appDataDir: String = when {
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

			// Ensure to create the directory if it doesn't exist
			val directory = Path.of(appDataDir)
			if (!Files.isDirectory(directory)) {
				Files.createDirectories(directory)
			}

			return ApplicationPaths(directory, appName)
		}
	}
}