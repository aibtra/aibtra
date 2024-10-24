/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.Logger
import dev.aibtra.diff.DiffManager
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

class PasteAndSubmitAction(
	environment: Environment,
	requestManager: RequestManager,
	profileManager: ProfileManager,
	diffManager: DiffManager,
	rawTextArea: RawTextArea,
	submitAction: SubmitAction
) :
	MainMenuAction("pasteAndSubmit", "Paste and Submit", Icons.PASTE, "Paste and Submit", null, environment.accelerators, {
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
			try {
				val text = clipboard.getData(DataFlavor.stringFlavor) as String
				rawTextArea.initializeText(text)
				diffManager.updateRawText(text, profileManager.profile().diffConfig, normalization = DiffManager.Normalization.initialize, callback = {
					submitAction.perform()
				})
			} catch (e: UnsupportedFlavorException) {
				LOG.error(e)
			} catch (ioe: IOException) {
				LOG.error(ioe)
			}
		}
	}) {

	init {
		requestManager.addProgressListener { inProgress ->
			isEnabled = !inProgress
		}
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}


