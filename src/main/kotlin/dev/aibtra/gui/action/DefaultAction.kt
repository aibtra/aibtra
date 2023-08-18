/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.action

import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

open class DefaultAction(
	val title: String,
	private val actionListener: ActionRunnable
) : AbstractAction(title) {
	final override fun actionPerformed(e: ActionEvent) {
		perform()
	}

	fun isSelectable(): Boolean {
		return getValue(PROPERTY_SELECTABLE) as? Boolean ?: false
	}

	fun setSelectable(selectable: Boolean) {
		putValue(PROPERTY_SELECTABLE, selectable)
	}

	fun isSelected(): Boolean {
		return getValue(SELECTED_KEY) as? Boolean ?: false
	}

	fun setSelected(selected: Boolean) {
		putValue(Action.SELECTED_KEY, selected)
	}

	fun perform() {
		actionListener.run(this)
	}

	companion object {
		const val PROPERTY_SELECTABLE = "selectable"
		const val PROPERTY_ENABLED = "enabled"
	}
}