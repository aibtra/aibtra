/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

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
		fun getLogger(kClass: KClass<*>): dev.aibtra.core.Logger {
			return Logger(Logger.getLogger(kClass.qualifiedName))
		}
	}
}