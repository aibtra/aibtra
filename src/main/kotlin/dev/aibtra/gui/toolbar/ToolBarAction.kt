/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.toolbar

import dev.aibtra.gui.Icon
import dev.aibtra.gui.action.ActionRunnable
import dev.aibtra.gui.action.DefaultAction

open class ToolBarAction(
	title: String,
	toolBarIcon: Icon? = null,
	toolBarText: String? = null,
	actionListener: ActionRunnable
) : DefaultAction(title, actionListener) {

	var toolBarIcon: Icon? = null
		set(value) {
			val oldValue = field
			field = value
			firePropertyChange(PROPERTY_TOOLBAR_ICON, oldValue, value)
		}

	val toolBarText: String?

	init {
		require((toolBarIcon == null) == (toolBarText == null))

		this.toolBarIcon = toolBarIcon
		this.toolBarText = toolBarText
	}

	companion object {
		const val PROPERTY_TOOLBAR_ICON = "toolBarIcon"
	}
}