/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui

import dev.aibtra.core.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

typealias Update = suspend () -> Unit
typealias Callback = (Update) -> Unit
typealias Run = suspend (Callback, CoroutineScope) -> Unit

class SequentialRunner(private val mainScope: CoroutineScope, private val mainDispatcher: CoroutineDispatcher, private val threadDispatcher: CoroutineDispatcher, private val exceptionHandler: (Throwable) -> Unit) {
	private val threadScope = CoroutineScope(EmptyCoroutineContext)
	private var currentJob: Job? = null

	fun schedule(run: Run, cancelCurrent: Boolean) {
		launchSafe(mainScope, mainDispatcher, exceptionHandler) {
			currentJob?.let {
				if (it.isCompleted || cancelCurrent) {
					it.cancel()
				}
				else {
					return@launchSafe
				}
			}

			val job = AtomicReference<Job>()
			job.set(launchSafe(threadScope, threadDispatcher, exceptionHandler) {
				run({ update: Update ->
					launchSafe(mainScope, mainDispatcher, exceptionHandler) {
						if (job.get() == currentJob) {
							update()
						}
					}
				}, this)
			})

			currentJob = job.get()
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
		fun createGuiThreadRunner(threadDispatcher: CoroutineDispatcher, mainScope: CoroutineScope): SequentialRunner {
			return SequentialRunner(mainScope, Dispatchers.Main, threadDispatcher) {
				throwable -> GlobalExceptionHandler.handle(throwable)
			}
		}
	}
}
