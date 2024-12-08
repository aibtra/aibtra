/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.action.*
import dev.aibtra.main.content.*

class ResolverResolveOnlyAction(
	private val resolverManager: ResolverManager,
	private val requestManager: ResolverRequestManager,
	accelerators: Accelerators
) :
	MainMenuAction("resolveOnly", "Resolve Only", Icons.RESOLVE_ONLY, "Resolve Only", "shift F5", accelerators, ActionRunnable {
		val state = resolverManager.state
		state.snippets?.let { snippets ->
			state.resolutions?.let { resolutions ->
				requestManager.submit(ResolverRequestManager.Request(snippets.files.overviewFile, false, resolutions.resolverPacket))
			}
		}
	}) {

	init {
		resolverManager.addStateListener { _, _ ->
			updateEnabledState()
		}

		requestManager.addProgressListener {
			updateEnabledState()
		}

		updateEnabledState()
	}

	private fun updateEnabledState() {
		isEnabled = !requestManager.inProgress && resolverManager.state.resolutions != null
	}
}

