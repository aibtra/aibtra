/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.configuration.*
import dev.aibtra.gui.action.*
import dev.aibtra.main.content.*
import dev.aibtra.openai.*
import java.util.concurrent.atomic.*

class ResolverRebuildAction(
	resolverManager: ResolverManager,
	resolverSaver: ResolverSaver,
	requestManager: ResolverRequestManager,
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuAction("rebuild", "Rebuild", Icons.REBUILD, "Rebuild", "F5", accelerators, ActionRunnable {
			action -> (action as ResolverRebuildAction).worker.run()
	}) {

	private val worker: Worker = Worker(this, resolverSaver, resolverManager, requestManager, configurationProvider)

	class Worker(
		private val action: ResolverRebuildAction,
		private val saver: ResolverSaver,
		private val resolverManager: ResolverManager,
		private val requestManager: ResolverRequestManager,
		private val configurationProvider: ConfigurationProvider
	) {
		private val stopMode = AtomicBoolean(false)

		fun run() {
			if (stopMode.get()) {
				requestManager.stopCurrent()
			}
			else {
				saver.checkSave { snippets ->
					snippets?.let {
						val configuration = configurationProvider.get(OpenAIResolverConfiguration)
						requestManager.submit(ResolverRequestManager.Request(snippets.files.overviewFile, false, null, configuration.profiles[0]))
					}
				}
			}
		}

		init {
			requestManager.addProgressListener { inProgress ->
				stopMode.set(inProgress)
				action.toolBarIcon = if (inProgress) Icons.STOP else Icons.REBUILD
			}

			resolverManager.addStateListener { _, _ ->
				updateEnabledState()
			}

			updateEnabledState()
		}

		private fun updateEnabledState() {
			action.isEnabled = resolverManager.state.snippets != null
		}
	}
}

