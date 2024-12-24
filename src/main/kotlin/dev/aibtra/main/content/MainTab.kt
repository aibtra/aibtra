/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.content

import dev.aibtra.gui.dialogs.*
import dev.aibtra.gui.toolbar.*
import java.awt.*
import javax.swing.*

internal abstract class MainTab(private val tabbedPane: MainTabbedPane, val environment: Environment, val dialogDisplayer: DialogDisplayer) {

	private val toggleDarkModeAction: ToggleDarkModeAction

	init {
		toggleDarkModeAction = ToggleDarkModeAction(environment.theme, environment.configurationProvider, environment.accelerators)
	}

	val control: Component by lazy {
		val overlayPanel = JPanel()
		overlayPanel.background = Color(0, 0, 0, 0)
		overlayPanel.isVisible = false
		overlayPanel.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)

		val mainPanel = JPanel()
		mainPanel.layout = OverlayLayout(mainPanel)
		mainPanel.add(overlayPanel)
		init(mainPanel, overlayPanel)
		mainPanel
	}

	abstract fun init(mainPanel: JPanel, overlayPanel: JPanel)

	val menuBar: JMenuBar by lazy {
		val menuBar = JMenuBar()
		val fileMenu = JMenu("File")
		if (addFileActions(fileMenu)) {
			fileMenu.addSeparator()
		}
		addAction(fileMenu, ExitAction(environment))
		menuBar.add(fileMenu)

		val editMenu = JMenu("Edit")
		if (addEditActions(editMenu)) {
			editMenu.addSeparator()
		}
		if (GuiConfiguration.isHotkeySupported()) {
			addAction(editMenu, ToggleHotkeyAction(environment.hotkeyListener, environment.configurationProvider, environment.accelerators, this.dialogDisplayer))
		}
		menuBar.add(editMenu)

		val viewMenu = JMenu("View")
		if (addViewActions(viewMenu)) {
			viewMenu.addSeparator()
		}
		addAction(viewMenu, toggleDarkModeAction)
		if (GuiConfiguration.isSystemTraySupported()) {
			addAction(viewMenu, ToggleSystemTrayAction(environment.configurationProvider, environment.accelerators))
		}
		menuBar.add(viewMenu)

		val profileMenu = JMenu("Profile")
		if (addProfileActions(profileMenu)) {
			menuBar.add(profileMenu)
		}

		val schemeMenu = JMenu("Scheme")
		if (addSchemeActions(schemeMenu)) {
			menuBar.add(schemeMenu)
		}

		val helpMenu = JMenu("Help")
		addAction(helpMenu, AcknowledgmentsAction(environment, this.dialogDisplayer))
		helpMenu.addSeparator()
		addAction(helpMenu, AboutAction(environment, this.dialogDisplayer))
		menuBar.add(helpMenu)
		menuBar
	}

	protected open fun addFileActions(fileMenu: JMenu) : Boolean {
		return false
	}

	protected open fun addEditActions(editMenu: JMenu) : Boolean {
		return false
	}

	protected open fun addViewActions(viewMenu: JMenu) : Boolean {
		return false
	}

	protected open fun addProfileActions(profileMenu: JMenu) : Boolean {
		return false
	}

	protected open fun addSchemeActions(menu: JMenu): Boolean {
		return false
	}

	val toolBar: ToolBar by lazy {
		val toolBar = ToolBar(environment.theme, true)
		fillToolBarLeft(toolBar)
		toolBar.add(Box.createHorizontalGlue())
		fillToolBarRight(toolBar)
		toolBar.add(toggleDarkModeAction)
		toolBar
	}

	protected open fun fillToolBarLeft(bar: ToolBar) {
	}

	protected open fun fillToolBarRight(bar: ToolBar) {
	}

	abstract fun getTitle(): String

	protected fun updateTitle() {
		tabbedPane.updateTitle(this)
	}

	internal open fun focusGained() {
	}

	internal abstract fun dispose()

	open fun checkClose(runnable: Runnable) {
		runnable.run()
	}

	fun toFront() {
		tabbedPane.activate(this)
	}

	abstract fun closed()

	companion object {
		fun addAction(menu: JMenu, action: MainMenuAction) {
			val radioButtonGroup = action.getRadioButtonGroup()
			menu.add(
				if (radioButtonGroup != null) {
					val item = JRadioButtonMenuItem(action)
					radioButtonGroup.add(item)
					item
				}
				else if (action.isSelectable()) {
					JCheckBoxMenuItem(action)
				}
				else {
					JMenuItem(action)
				}
			)
		}
	}
}