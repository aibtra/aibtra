/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui

import dev.aibtra.core.GlobalExceptionHandler
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

typealias Update = suspend () -> Unit
typealias Callback = (Update) -> Unit
typealias Run = suspend (Callback, CoroutineScope) -> Unit

class SequentialRunner(private val mainScope: CoroutineScope, private val mainDispatcher: CoroutineDispatcher, private val threadDispatcher: CoroutineDispatcher, private val exceptionHandle: (Throwable) -> Unit) {
	private val threadScope = CoroutineScope(EmptyCoroutineContext)
	private var currentJob: Job? = null

	fun schedule(run: Run, cancelCurrent: Boolean) {
		launchSafe(mainScope, mainDispatcher, exceptionHandle) {
			currentJob?.let {
				if (it.isCompleted || cancelCurrent) {
					it.cancel()
				}
				else {
					return@launchSafe
				}
			}

			currentJob = launchSafe(threadScope, threadDispatcher, exceptionHandle) {
				run({ update: Update ->
					launchSafe(mainScope, mainDispatcher, exceptionHandle) {
						update()
					}
				}, this)
			}
		}
	}

	private fun launchSafe(
		scope: CoroutineScope,
		dispatcher: CoroutineDispatcher,
		exceptionHandle: (Throwable) -> Unit,
		block: suspend CoroutineScope.() -> Unit
	): Job {
		return scope.launch(dispatcher, CoroutineStart.DEFAULT) {
			try {
				block()
			} catch (th: Throwable) {
				exceptionHandle(th)
			}
		}
	}

	companion object {
		fun createGuiThreadRunner(threadDispatcher: CoroutineDispatcher): SequentialRunner {
			return SequentialRunner(MainScope(), Dispatchers.Main, threadDispatcher) {
				throwable -> GlobalExceptionHandler.handle(throwable)
			}
		}
	}
}
