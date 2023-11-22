/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.gui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class SequentialRunnerTest {

	@Test
	fun test() {
		val threadDispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()
		val mainDispatcher: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
		val mainScope = CoroutineScope(EmptyCoroutineContext)
		val failure = AtomicReference<Throwable>()
		val runner = SequentialRunner(mainScope, mainDispatcher, threadDispatcher) { throwable ->
			failure.set(throwable)
		}

		val mainThread = Thread.currentThread()
		val random = Random(0)
		for (i in 0 until RUNS) {
			Thread.sleep(random.nextLong(10))
			runner.schedule(object : Run {
				override suspend fun invoke(callback: Callback, p2: CoroutineScope) {
					Assertions.assertTrue(Thread.currentThread() != mainThread)
					@Suppress("BlockingMethodInNonBlockingContext")
					Thread.sleep(random.nextLong(10))

					Assertions.assertEquals(threadDispatcher, coroutineContext[ContinuationInterceptor])

					callback {
						Assertions.assertEquals(mainDispatcher, coroutineContext[ContinuationInterceptor])
					}
				}
			}, true)
		}

		failure.get()?.let {
			throw it
		}
	}

	companion object {
		const val RUNS = 100
	}
}