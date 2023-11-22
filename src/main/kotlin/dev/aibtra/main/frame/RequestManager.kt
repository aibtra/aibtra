/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.diff.DiffManager
import dev.aibtra.gui.Callback
import dev.aibtra.gui.Run
import dev.aibtra.gui.SequentialRunner
import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class RequestManager(
	private val diffManager: DiffManager,
	coroutineDispatcher: CoroutineDispatcher,
	private val dialogDisplayer: DialogDisplayer
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(coroutineDispatcher)
	private val inProgressListeners = ArrayList<InProgressListener>()
	private val currentRun: AtomicReference<Run?> = AtomicReference(null)
	var inProgress = false
		private set

	fun schedule(operation: Operation) {
		val filtered = diffManager.state.filtered
		val run = object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				notifyInProgress(true)

				callback {
					diffManager.updateRefined("", false) // signal started, so diff becomes reset
				}

				var lastRefined: String? = null
				try {
					operation.run(filtered.clean) { refined ->
						lastRefined = refined

						callback {
							val res = filtered.recreate(refined)
							diffManager.updateRefined(res.getOrThrow(), false)
						}

						this == currentRun.get()
					}
				} catch (ioe: IOException) {
					Dialogs.showIOError(ioe, dialogDisplayer)
				} finally {
					if (this == currentRun.get()) {
						lastRefined?.let {
							callback {
								val res = filtered.recreate(it)
								if (res.isFailure) {
									diffManager.updateRefined("<FAILURE>", true)
								}
								else {
									diffManager.updateRefined(res.getOrThrow(), true)
								}
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

	fun interface OpCallback {
		fun callback(refined: String): Boolean
	}

	fun interface Operation {
		fun run(filtered: String, callback: OpCallback)
	}
}