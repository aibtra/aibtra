package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable

@Serializable
internal data class MainLayout(val x: Int? = null, val y: Int? = null, val width: Int = DEFAULT_WIDTH, val height: Int = DEFAULT_HEIGHT, val maximized: Boolean = false, val dividerLocation: Int = 0) {
	companion object : ConfigurationFactory<MainLayout> {
		val DEFAULT_WIDTH: Int
		val DEFAULT_HEIGHT: Int

		init {
			DEFAULT_WIDTH = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 20) 1000 else 1500
			DEFAULT_HEIGHT = DEFAULT_WIDTH * 2 / 3
		}

		override fun name(): String {
			return "frame-layout"
		}

		override fun default(): MainLayout {
			return MainLayout()
		}
	}
}