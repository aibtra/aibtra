/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.gui.Icon
import dev.aibtra.gui.action.ActionRunnable
import dev.aibtra.gui.toolbar.ToolBarAction
import javax.swing.KeyStroke

open class MainMenuAction(
	private val id: String,
	title: String,
	toolBarIcon: Icon? = null,
	toolBarText: String? = null,
	keyStrokeDefault: String? = null,
	accelerators: Accelerators?,
	actionListener: ActionRunnable
) : ToolBarAction(title, toolBarIcon, toolBarText, actionListener) {

	constructor(id: String, title: String, keyStrokeDefault: String? = null, accelerators: Accelerators?, actionListener: ActionRunnable) : this(id, title, null, null, keyStrokeDefault, accelerators, actionListener)

	init {
		accelerators?.let {
			accelerators.get(id, KeyStroke.getKeyStroke(keyStrokeDefault))?.let {
				putValue(ACCELERATOR_KEY, it)
			}
		}
	}
}