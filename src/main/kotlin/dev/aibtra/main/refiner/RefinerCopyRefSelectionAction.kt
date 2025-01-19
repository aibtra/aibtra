/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.gui.action.*

class RefinerCopyRefSelectionAction(
	refEditor: RefinerRefEditor
) :
	DefaultAction("Copy", ActionRunnable {
		refEditor.copySelectionToClipboard()
	})

