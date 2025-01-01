/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.refiner.*
import dev.aibtra.text.*
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.atomic.*

class RefinerRequestManager(
	private val diffManager: RefinerDiffManager,
	coroutineDispatcher: CoroutineDispatcher,
	mainScope: CoroutineScope,
	private val dialogDisplayer: DialogDisplayer
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(coroutineDispatcher, mainScope)
	private val inProgressListeners = ArrayList<InProgressListener>()
	private val currentRun: AtomicReference<Run?> = AtomicReference(null)
	var inProgress = false
		private set

	fun schedule(request: Request, failureHandler: RequestManagerFailureHandler) {
		LOG.debug("schedule")

		val run = object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				notifyInProgress(true)

				val state = diffManager.state
				LOG.debug("invoke: $state")

				val filteredText = state.filtered
				val priorConversation = state.conversation
				callback {
					diffManager.updateRefText("", null) // signal started, so diff becomes reset
				}

				var lastRef: String? = null
				var lastConversation: RefinerConversation? = null
				try {
					request.run(filteredText, priorConversation, { ref, conversation ->
						lastRef = ref
						lastConversation = conversation

						val current = this == currentRun.get()
						if (current) {
							callback {
								diffManager.updateRefText(ref, null)
							}
						}

						current
					}, { failure, mightBeAuthentication ->
						if (this == currentRun.get()) {
							failureHandler.process(failure, mightBeAuthentication)
						}
					})
				} catch (ioe: IOException) {
					Dialogs.showIOError(ioe, dialogDisplayer)
				} finally {
					if (this == currentRun.get()) {
						lastRef?.let { ref ->
							callback {
								diffManager.updateRefText(ref, lastConversation)
							}
						}
						inProgress = false
						notifyInProgress(false)
					}
				}
			}
		}
		currentRun.set(run)
		if (!inProgress) {
			inProgress = true
			notifyInProgress(inProgress)
		}
		sequentialRunner.schedule(run, true)
	}

	fun stopCurrent() {
		Ui.assertEdt()

		currentRun.set(null)
		inProgress = false
		notifyInProgress(false)
	}

	fun addProgressListener(listener: InProgressListener) {
		inProgressListeners.add(listener)
	}

	private fun notifyInProgress(inProgress: Boolean) {
		Ui.runInEdt {
			for (inProgressListener in inProgressListeners) {
				inProgressListener.setInProgress(inProgress)
			}
		}
	}

	fun interface InProgressListener {
		fun setInProgress(inProgress: Boolean)
	}

	fun interface RequestCallback {
		fun callback(ref: String, conversation: RefinerConversation?): Boolean
	}

	fun interface Request {
		fun run(filtered: FilteredText, priorConversation: RefinerConversation?, callback: RequestCallback, failureHandler: RequestManagerFailureHandler)
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}