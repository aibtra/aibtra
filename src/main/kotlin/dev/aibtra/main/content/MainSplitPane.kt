package dev.aibtra.main.content

import java.awt.*
import java.awt.event.*
import javax.swing.*

class MainSplitPane(leftComponent: Component, rightComponent: Component, val environment: Environment) {
	val control: Component

	init {
		val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
		splitPane.resizeWeight = 0.5
		splitPane.topComponent = leftComponent
		splitPane.bottomComponent = rightComponent
		var splitInitializing = true
		splitPane.addComponentListener(object : ComponentAdapter() {
			override fun componentResized(e: ComponentEvent?) {
				if (splitInitializing) {
					splitInitializing = false
					updateSplitPaneDividerLocation(splitPane)
				}
			}
		})
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
			if (!splitInitializing) {
				environment.configurationProvider.change(MainLayout) {
					it.copy(dividerLocation = splitPane.dividerLocation)
				}
			}
		}

		// To prevent an initial jumping from centered location to stored location in componentResized()
		updateSplitPaneDividerLocation(splitPane)

		control = splitPane
	}

	private fun updateSplitPaneDividerLocation(splitPane: JSplitPane) {
		val location = environment.configurationProvider.get(MainLayout).dividerLocation
		if (location > 0) {
			splitPane.dividerLocation = location
		}
	}
}