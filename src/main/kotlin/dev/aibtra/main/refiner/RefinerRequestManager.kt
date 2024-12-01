/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.diff.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.text.*
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.atomic.*

class RefinerRequestManager(
	private val diffManager: DiffManager,
	coroutineDispatcher: CoroutineDispatcher,
	mainScope: CoroutineScope,
	private val dialogDisplayer: DialogDisplayer
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(coroutineDispatcher, mainScope)
	private val inProgressListeners = ArrayList<InProgressListener>()
	private val currentRun: AtomicReference<Run?> = AtomicReference(null)
	var inProgress = false
		private set

	fun schedule(request: Request) {
		val run = object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				notifyInProgress(true)

				val filteredText = diffManager.state.filtered
				callback {
					diffManager.updateRefText("", false) // signal started, so diff becomes reset
				}

				var lastRef: String? = null
				try {
					request.run(filteredText) { ref ->
						lastRef = ref

						callback {
							diffManager.updateRefText(ref, false)
						}

						this == currentRun.get()
					}
				} catch (ioe: IOException) {
					Dialogs.showIOError(ioe, dialogDisplayer)
				} finally {
					if (this == currentRun.get()) {
						lastRef?.let { ref ->
							callback {
								diffManager.updateRefText(ref, true)
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
		fun callback(ref: String): Boolean
	}

	fun interface Request {
		fun run(filtered: FilteredText, callback: RequestCallback)
	}
}