/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

import dev.aibtra.main.startup.MainStartup
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass

class Logger private constructor(private val logger: Logger) {
	fun info(message: String?) {
		logger.log(Level.INFO, message)
	}

	fun warn(message: String?) {
		logger.log(Level.WARNING, message)
	}

	fun error(message: String) {
		logger.log(Level.SEVERE, message)
	}

	fun error(th: Throwable) {
		logger.log(Level.SEVERE, th.message, th)
	}

	fun error(message: String, ex: Throwable) {
		logger.log(Level.SEVERE, message, ex)
	}

	companion object {
		private var logFile: Path? = null
		private var fileHandler: FileHandler? = null

		fun getLogger(kClass: KClass<*>): dev.aibtra.core.Logger {
			return Logger(Logger.getLogger(kClass.qualifiedName))
		}

		fun setup(logFile: Path) {
			this.logFile = logFile

			val logger: Logger = Logger.getLogger("")
			fileHandler = FileHandler(logFile.toString()).apply {
				formatter = MainStartup.LogFormatter()
			}
			logger.addHandler(fileHandler)
			logger.level = Level.INFO
		}

		fun backup(prefix: String) {
			fileHandler?.flush()
			logFile?.let {
				Files.copy(it, Files.createTempFile(it.parent, "$prefix-", ".txt"), StandardCopyOption.REPLACE_EXISTING)
			}
		}
	}
}