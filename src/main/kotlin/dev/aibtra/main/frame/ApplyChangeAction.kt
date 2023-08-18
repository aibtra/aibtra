/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.diff.DiffManager
import dev.aibtra.gui.action.ActionRunnable

class ApplyChangeAction(
	refinedTextArea: RefinedTextArea,
	rawTextArea: RawTextArea,
	diffManager: DiffManager,
	accelerators: Accelerators
) :
	MainMenuAction("applyChange", "Apply", Icons.ACCEPT, "Apply", "alt LEFT", accelerators, ActionRunnable {
		val state = diffManager.state
		require(rawTextArea.getText() == state.raw)

		refinedTextArea.getSelectionRange()?.let {
			for (block in DiffManager.getSelectedBlocksFromRefined(state, it).reversed()) {
				rawTextArea.replaceText(block.rawFrom, block.rawTo, state.ref.substring(block.refFrom, block.refTo))
			}
		}
	}) {
	init {
		refinedTextArea.addSelectionListener { range ->
			val state = diffManager.state
			isEnabled = range?.let {
				DiffManager.getSelectedBlocksFromRefined(state, it).isNotEmpty()
			} ?: false
		}

		isEnabled = false
	}
}

