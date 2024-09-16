/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.Logger
import dev.aibtra.diff.DiffManager
import dev.aibtra.gui.action.ActionRunnable
import java.util.stream.Collectors

class ApplyChangeAction(
	refTextArea: RefTextArea,
	rawTextArea: RawTextArea,
	diffManager: DiffManager,
	accelerators: Accelerators
) :
	MainMenuAction("applyChange", "Apply", Icons.ACCEPT, "Apply", "alt LEFT", accelerators, ActionRunnable {
		val state = diffManager.state
		val rawText = state.raw
		val refText = state.ref
		require(rawTextArea.getText() == rawText)

		refTextArea.getSelectionRange()?.let {
			val blocks = DiffManager.getSelectedBlocksFromRef(state, it).reversed()
			LOG.info("Apply " + blocks.stream().map {
				"(${it.rawFrom}-${it.rawTo} '${rawText.substring(it.rawFrom, it.rawTo).replace("\n", "\\n")}')" +
								"->" +
								"(${it.refFrom}-${it.refTo} '${refText.substring(it.refFrom, it.refTo).replace("\n", "\\n")}')"
			}.collect(Collectors.joining(",")))

			for (block in blocks) {
				rawTextArea.replaceText(block.rawFrom, block.rawTo, state.ref.substring(block.refFrom, block.refTo))
			}
		}
	}) {
	init {
		refTextArea.addSelectionListener { range ->
			val state = diffManager.state
			isEnabled = range?.let {
				DiffManager.getSelectedBlocksFromRef(state, it).isNotEmpty()
			} ?: false
		}

		isEnabled = false
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}

