/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.diff.*
import dev.aibtra.main.content.*
import java.awt.*
import java.awt.datatransfer.*
import java.io.*

class RefinerPasteAndSubmitAction(
	environment: Environment,
	requestManager: RefinerRequestManager,
	profileManager: RefinerProfileManager,
	diffManager: DiffManager,
	rawTextArea: RefinerRawTextArea,
	submitAction: RefinerSubmitAction
) :
	MainMenuAction("pasteAndSubmit", "Paste and Submit", Icons.PASTE, "Paste and Submit", null, environment.accelerators, {
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
			try {
				val text = clipboard.getData(DataFlavor.stringFlavor) as String
				rawTextArea.initializeText(text)
				diffManager.updateRawText(text, null, profileManager.profile().diffConfig, normalization = DiffManager.Normalization.INITIALIZE, callback = {
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


