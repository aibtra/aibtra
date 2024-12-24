/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.action.*
import java.awt.*
import java.awt.datatransfer.*

class RefinerCopyRefSelectionAction(
	refEditor: RefinerRefEditor
) :
	DefaultAction("Copy", ActionRunnable {
		val selection = StringSelection(refEditor.getSelectionText())
		val clipboard = Toolkit.getDefaultToolkit().systemClipboard
		clipboard.setContents(selection, selection)
	})

