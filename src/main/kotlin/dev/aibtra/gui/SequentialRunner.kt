/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui

import dev.aibtra.core.GlobalExceptionHandler
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

typealias Update = () -> Unit
typealias Callback = (Update) -> Unit
typealias Run = suspend (Callback, CoroutineScope) -> Unit

fun CoroutineScope.launchSafe(
	context: kotlin.coroutines.CoroutineContext = EmptyCoroutineContext,
	start: CoroutineStart = CoroutineStart.DEFAULT,
	block: suspend CoroutineScope.() -> Unit
): Job {
	return launch(context, start) {
		try {
			block()
		} catch (th: Throwable) {
			GlobalExceptionHandler.handle(th)
		}
	}
}

class SequentialRunner(private val dispatcher: CoroutineDispatcher) {
	private val mainScope = MainScope()
	private var currentJob: Job? = null

	fun schedule(run: Run, cancelCurrent: Boolean) {
		mainScope.launchSafe(Dispatchers.Main) {
			currentJob?.let {
				if (it.isCompleted || cancelCurrent) {
					it.cancel()
				}
				else {
					return@launchSafe
				}
			}

			currentJob = launchSafe(dispatcher) {
				run({ update: Update ->
					mainScope.launchSafe(Dispatchers.Main) {
						update()
					}
				}, this)
			}
		}
	}
}
