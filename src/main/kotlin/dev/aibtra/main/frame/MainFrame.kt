/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.core.WorkingMode
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.DialogDisplayer.Companion.create
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import javax.swing.*

class MainFrame(private val environment: Environment) {
	val dialogDisplayer: DialogDisplayer

	private val frame: JFrame
	private val centerPane: Container
	private val tabbedPane: MainTabbedPane

	init {
		frame = JFrame(FRAME_TITLE)
		frame.iconImage = Icons.LOGO.getImageIcon(true).image

		centerPane = Container()
		centerPane.layout = BorderLayout()

		tabbedPane = MainTabbedPane(centerPane, frame, environment.frameManager)

		dialogDisplayer = create(frame)
	}

	fun show() {
		val layout = environment.configurationProvider.get(MainLayout)
		frame.preferredSize = Dimension(layout.width, layout.height)
		layout.x?.let { x ->
			layout.y?.let { y ->
				frame.location = Point(x, y)
			}
		}
		frame.pack()

		if (layout.maximized) {
			frame.extendedState = Frame.MAXIMIZED_BOTH
		}

		frame.jMenuBar = JMenuBar()

		val pane = frame.contentPane
		pane.layout = BorderLayout()
		pane.add(centerPane, BorderLayout.CENTER)

		centerPane.add(tabbedPane.control, BorderLayout.CENTER)

		frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
		frame.isVisible = true

		environment.frameManager.register(this, frame, ::exitOnClose)

		frame.addWindowListener(object : WindowAdapter() {
			override fun windowClosing(e: WindowEvent?) {
				checkClose {
					frame.dispose()
				}
			}

			override fun windowClosed(e: WindowEvent?) {
				tabbedPane.dispose()
			}
		})
	}

	private fun exitOnClose(): Boolean {
		return !environment.systemTrayEnabled && !environment.configurationProvider.get(GuiConfiguration).hotkeyEnabled
	}

	fun checkClose(runnable: Runnable) {
		tabbedPane.checkClose(runnable)
	}

	fun closed() {
		val location = frame.location
		val size = frame.size
		environment.configurationProvider.change(MainLayout) {
			if ((frame.extendedState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
				it.copy(maximized = true)
			}
			else {
				it.copy(x = location.x, y = location.y, width = size.width, height = size.height, maximized = false)
			}
		}

		tabbedPane.closed()
	}

	fun openEmpty(profileId: String?) {
		val tab = MainTextTab(WorkingMode.OPEN, tabbedPane, environment, dialogDisplayer)
		tab.setProfile(profileId)
		tabbedPane.add(tab)
	}

	fun openText(text: String, workingMode: WorkingMode, profileId: String?) {
		val tab = MainTextTab(WorkingMode.CLIPBOARD, tabbedPane, environment, dialogDisplayer)
		tab.setText(text, workingMode, profileId)
		tabbedPane.add(tab)
	}

	fun openFile(fileToOpen: Path, profileId: String?, line: Int?) {
		val tab = MainFileTab(tabbedPane, environment, dialogDisplayer)
		tab.setFile(fileToOpen, profileId, line)
		tabbedPane.add(tab)
	}

	fun toFront() {
		frame.toFront()
	}

	companion object {
		const val FRAME_TITLE = "Aibtra 1.0 alpha"
	}
}