/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

import java.io.*
import java.nio.file.*
import java.time.*
import java.time.format.*
import java.util.logging.*
import java.util.logging.Logger
import kotlin.reflect.*

class Logger private constructor(private val logger: Logger) {
	fun trace(message: String?) {
		logger.log(Level.FINEST, message)
	}

	fun debug(message: String?) {
		logger.log(Level.FINE, message)
	}

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

			val configFile = logFile.parent.resolve("logger.properties")
			if (Files.isRegularFile(configFile)) {
				System.setProperty("java.util.logging.config.file", configFile.toString())
				LogManager.getLogManager().readConfiguration()
			}
			else {
				Logger.getLogger("").level = Level.INFO
			}

			val logger: Logger = Logger.getLogger("")
			fileHandler = FileHandler(logFile.toString()).apply {
				formatter = LogFormatter()
			}
			logger.addHandler(fileHandler)
		}

		fun backup(prefix: String) {
			fileHandler?.flush()
			logFile?.let {
				Files.copy(it, Files.createTempFile(it.parent, "$prefix-", ".txt"), StandardCopyOption.REPLACE_EXISTING)
			}
		}
	}

	private class LogFormatter : Formatter() {
		override fun format(record: LogRecord): String {
			val stringBuilder = StringBuilder()
			stringBuilder.append("${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} [${Thread.currentThread().name}] ${record.level}: ${record.message}\n")

			record.thrown?.let {
				val stringWriter = StringWriter()
				val printWriter = PrintWriter(stringWriter)
				it.printStackTrace(printWriter)
				printWriter.flush()

				stringBuilder.append(stringWriter.toString()).append('\n')
			}

			return stringBuilder.toString()
		}
	}
}