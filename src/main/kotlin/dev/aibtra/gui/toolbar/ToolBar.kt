/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.toolbar

import dev.aibtra.gui.Ui
import dev.aibtra.gui.action.DefaultAction
import dev.aibtra.main.frame.Theme
import java.awt.Component
import javax.swing.AbstractButton
import javax.swing.Action
import javax.swing.JToggleButton
import javax.swing.JToolBar

class ToolBar(
	private val theme: Theme,
	private val withText: Boolean
) {
	private val toolBar: JToolBar = JToolBar()
	private val actionToButton = HashMap<ToolBarAction, AbstractButton>()
	private val themeListener: (Theme) -> Unit

	init {
		themeListener = {
			updateIcons()
		}

		theme.addChangeListener(themeListener)
	}

	fun add(action: ToolBarAction) {
		val text = if (withText) action.toolBarText else null
		val button: AbstractButton = if (action.isSelectable()) {
			val button = JToggleButton(text)
			button.isSelected = action.isSelected()
			action.addPropertyChangeListener { evt ->
				if (evt?.propertyName == Action.SELECTED_KEY) {
					button.isSelected = action.isSelected()
				}
			}
			button.addActionListener(action)
			button
		}
		else {
			Ui.createButton(action)
		}

		actionToButton[action] = button
		updateIcon(action, button)

		button.isEnabled = action.isEnabled
		action.addPropertyChangeListener { evt ->
			if (evt.propertyName == DefaultAction.PROPERTY_ENABLED) {
				button.isEnabled = evt.newValue as? Boolean ?: false
			}
			else if (evt.propertyName == ToolBarAction.PROPERTY_TOOLBAR_ICON) {
				updateIcon(action, button)
			}
		}
		toolBar.add(button)
	}

	fun add(component: Component) {
		toolBar.add(component)
	}

	fun getComponent(): Component {
		return toolBar
	}

	fun dispose() {
		theme.removeChangeListener(themeListener)
	}

	private fun updateIcons() {
		for ((action, button) in actionToButton) {
			updateIcon(action, button)
		}
	}

	private fun updateIcon(action: ToolBarAction, button: AbstractButton) {
		button.icon = action.toolBarIcon?.getImageIcon(theme.dark)
	}
}