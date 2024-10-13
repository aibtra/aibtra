/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.core

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

class DebugLog(
	config: Config
) {
	private val debugDirectory = initializeDebugDirectory(config)
	private val debugStartTime = System.currentTimeMillis()

	fun run(title: String, task: (log: Log, logActive: Boolean) -> Unit) {
		if (debugDirectory == null) {
			task(object : Log {
				override fun println(line: String) {
				}
			}, false)
			return
		}

		val debugFile = Files.createTempFile(debugDirectory, "$debugStartTime-${System.currentTimeMillis()}-$title-", ".txt")
		PrintWriter(Files.newBufferedWriter(debugFile)).use { writer ->
			task(object : Log {
				override fun println(line: String) {
					writer.println(line)
				}
			}, true)
		}
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)

		fun initializeDebugDirectory(config: Config): Path? {
			if (config.directory == null) {
				return null
			}

			return try {
				val dir = Path.of(config.directory)
				Files.createDirectories(dir)
				dir
			} catch (e: Exception) {
				LOG.error(e)
				null
			}
		}
	}

	interface Log {
		fun println(line: String)
	}

	@Serializable
	data class Config(
		val directory: String? = null
	) {
		companion object : ConfigurationFactory<Config> {
			override fun name(): String = "debug"

			override fun default(): Config {
				return Config()
			}
		}
	}
}