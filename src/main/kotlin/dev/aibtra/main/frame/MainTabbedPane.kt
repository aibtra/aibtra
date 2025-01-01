package dev.aibtra.main.frame

import java.awt.*
import java.util.function.IntConsumer
import javax.swing.*

internal class MainTabbedPane(private val centerPane: Container, private val frame: JFrame, private val frameManager: FrameManager) {

	private val tabs: MutableList<MainTab> = mutableListOf()
	private val tabbedPane = JTabbedPane()
	val control: Component = tabbedPane

	init {
		tabbedPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Panel.border") ?: UIManager.getColor("Separator.foreground") ?: Color.BLACK)
		tabbedPane.putClientProperty("JTabbedPane.tabClosable", true)
		tabbedPane.putClientProperty("JTabbedPane.tabCloseCallback", IntConsumer { index ->
			val component = tabbedPane.getComponentAt(index)
			tabs.find { it.control == component }?.let {
				close(it)
			}
		})

		tabbedPane.addChangeListener {
			for (tab in tabs) {
				if (tab.control == tabbedPane.selectedComponent) {
					activate(tab)
					return@addChangeListener
				}
			}
		}
	}

	fun add(tab: MainTab) {
		tabbedPane.addTab(tab.getTitle(), tab.control)
		tabs.add(tab)
		activate(tab)
	}

	fun activate(tab: MainTab) {
		tabbedPane.selectedComponent = tab.control

		frame.jMenuBar = tab.menuBar

		val component = tab.toolBar.getComponent()
		for (comp in centerPane.components) {
			(comp as? JToolBar)?.let {
				centerPane.remove(comp)
			}
		}

		centerPane.add(component, BorderLayout.NORTH)
		centerPane.repaint()
	}

	fun updateTitle(tab: MainTab) {
		val index = tabbedPane.indexOfComponent(tab.control)
		require(index >= 0)

		tabbedPane.setTitleAt(index, tab.getTitle())
	}

	fun checkClose(runnable: Runnable) {
		checkClose(tabs, runnable)
	}

	fun closed() {
		for (tab in tabs) {
			tab.closed()
		}
	}

	fun dispose() {
		for (tab in tabs) {
			tab.dispose()
		}
	}

	private fun close(it: MainTab) {
		checkClose(listOf(it)) {
			tabs.remove(it)
			tabbedPane.remove(it.control)

			it.dispose()
			if (tabs.isEmpty()) {
				frameManager.close()
			}
		}
	}

	private fun checkClose(tabs: List<MainTab>, runnable: Runnable) {
		if (tabs.isEmpty()) {
			runnable.run()
			return
		}

		tabs[0].checkClose {
			checkClose(tabs.subList(1, tabs.size), runnable)
		}
	}

	fun <O> iterate(callback: (MainTab) -> O) : O? {
		for (tab in tabs) {
			callback(tab)?.let {
				return it
			}
		}

		return null
	}
}