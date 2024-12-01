/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.diff.*
import dev.aibtra.gui.action.*
import dev.aibtra.main.content.*
import java.util.concurrent.atomic.*

class RefinerSubmitAction(
	environment: Environment,
	diffManager: DiffManager,
	requestManager: RefinerRequestManager,
	submitter: RefinerSubmitter
) :
	MainMenuAction("submit", "Submit", Icons.SUBMIT, "Submit", "ctrl ENTER", environment.accelerators, ActionRunnable { action -> (action as RefinerSubmitAction).worker.run() }) {

	private val worker: Worker = Worker(this, submitter, diffManager, requestManager)

	class Worker(
		private val action: RefinerSubmitAction,
		private val submitter: RefinerSubmitter,
		diffManager: DiffManager,
		private val requestManager: RefinerRequestManager
	) {
		private val stopMode = AtomicBoolean(false)

		fun run() {
			if (stopMode.get()) {
				requestManager.stopCurrent()
			}
			else {
				submitter.run()
			}
		}

		init {
			requestManager.addProgressListener { inProgress ->
				stopMode.set(inProgress)
				action.toolBarIcon = if (inProgress) Icons.STOP else Icons.SUBMIT
			}

			diffManager.addStateListener { state, _ ->
				updateState(state)
			}

			updateState(diffManager.state)
		}

		private fun updateState(state: DiffManager.State) {
			action.isEnabled = state.diff.raw.isNotEmpty()
		}
	}
}