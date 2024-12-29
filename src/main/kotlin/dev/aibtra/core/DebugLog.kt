/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.core

import dev.aibtra.configuration.*
import kotlinx.serialization.Serializable
import java.io.*
import java.nio.file.*
import java.time.*
import java.time.format.*

class DebugLog(
	val config: Config,
	val settingsPath: Path
) {
	private val debugDirectory = initializeDebugDirectory(config, settingsPath)
	private val debugStartTime = System.currentTimeMillis()

	fun run(category: String, title: String, level: Level, task: (log: Log, logActive: Boolean) -> Unit) {
		if (!shallLog(level, category)) {
			task(object : Log {
				override fun println(line: String) {
				}
			}, false)
			return
		}

		PrintWriter(Files.newBufferedWriter(createDebugFile(category, title))).use { writer ->
			task(object : Log {
				override fun println(line: String) {
					writer.println(line)
					writer.flush()
				}
			}, true)
		}
	}

	fun log(category: String, title: String, level: Level, text: String) {
		if (shallLog(level, category)) {
			Files.newBufferedWriter(createDebugFile(category, title)).use { writer ->
				writer.write(text)
			}
		}
	}

	private fun shallLog(level: Level, category: String): Boolean {
		return debugDirectory != null &&
						level.precedence >= config.level.precedence &&
						!(config.categories.isNotEmpty() && !config.categories.contains(category))
	}

	private fun DebugLog.createDebugFile(category: String, title: String): Path? {
		val currentTime = LocalDateTime.now()
		val formattedTime = currentTime.format(DATE_TIME_FORMATTER)
		val now = System.currentTimeMillis()
		return Files.createTempFile(requireNotNull(debugDirectory), "$formattedTime-${String.format("%010d", now - debugStartTime)}-$category-$title-", ".txt")
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
		private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

		fun initializeDebugDirectory(config: Config, settingsPath: Path): Path? {
			if (config.directory == null) {
				return null
			}

			return try {
				val dir = settingsPath.resolve(Path.of(config.directory))
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
		val directory: String? = null,
		val level: Level = Level.DEBUG,
		val categories: Set<String> = setOf()
	) {
		companion object : ConfigurationFactory<Config> {
			override fun name(): String = "debug"

			override fun default(): Config {
				return Config()
			}
		}
	}

	@Serializable
	enum class Level(val precedence: Int) {
		DEBUG(0), INFO(1)
	}
}