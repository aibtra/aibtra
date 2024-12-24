/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.content

import com.formdev.flatlaf.*
import dev.aibtra.configuration.*
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.*
import java.util.*
import javax.swing.*


class Theme(val configurationProvider: ConfigurationProvider) {
	var dark = configurationProvider.get(GuiConfiguration).darkTheme
		private set

	private val listeners = ArrayList<(Theme) -> Unit>()

	fun addChangeListener(listener: (Theme) -> Unit) {
		listeners.add(listener)
	}

	fun removeChangeListener(listener: (Theme) -> Unit) {
		val removed = listeners.remove(listener)
		require(removed)
	}

	fun switch() {
		dark = !dark
		update()
	}

	fun update() {
		configurationProvider.change(GuiConfiguration) {
			it.copy(darkTheme = dark)
		}

		val guiColors = configurationProvider.get(GuiColors)
		val laf = if (dark) DarkLaf(guiColors) else LightLaf(guiColors)
		FlatLaf.setup(laf)

		val rSyntaxTextTheme = loadRSyntaxTextTheme(dark)
		fun iterateRSyntaxTextAreas(component: Component, consume: (RSyntaxTextArea) -> Unit) {
			if (component is RSyntaxTextArea) {
				consume(component)
			}
			if (component is Container) {
				for (child in component.components) {
					iterateRSyntaxTextAreas(child, consume)
				}
			}
		}

		for (frame in Frame.getFrames()) {
			SwingUtilities.updateComponentTreeUI(frame)
		}

		for (frame in Frame.getFrames()) {
			iterateRSyntaxTextAreas(frame) {
				rSyntaxTextTheme.apply(it)
				it.font = configurationProvider.get(GuiConfiguration).fonts.monospacedFont
			}
		}

		for (listener in listeners) {
			listener(this)
		}
	}

	companion object {
		fun applyRSyntaxTextTheme(textArea: RSyntaxTextArea, configurationProvider: ConfigurationProvider) {
			loadRSyntaxTextTheme(configurationProvider.get(GuiConfiguration).darkTheme).apply(textArea)
		}

		private fun loadRSyntaxTextTheme(dark: Boolean): org.fife.ui.rsyntaxtextarea.Theme {
			return if (dark) {
				loadRSyntaxTextTheme("dark.xml")
			}
			else {
				loadRSyntaxTextTheme("default.xml")
			}
		}

		private fun loadRSyntaxTextTheme(rSyntaxThemeName: String): org.fife.ui.rsyntaxtextarea.Theme {
			return org.fife.ui.rsyntaxtextarea.Theme.load(
				RSyntaxTextArea::class.java.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/${rSyntaxThemeName}")
			)
		}

		private fun toHex(color: Color): String {
			return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
		}
	}

	private class DarkLaf(val colors: GuiColors) : FlatDarkLaf() {
		override fun getAdditionalDefaults(): Properties {
			val properties = super.getAdditionalDefaults() ?: Properties()
			properties["@background"] = toHex(colors.dark.backgroundColor)
			properties["@foreground"] = toHex(colors.dark.foregroundColor)
			return properties
		}
	}

	private class LightLaf(val colors: GuiColors) : FlatLightLaf() {
		override fun getAdditionalDefaults(): Properties {
			val properties = super.getAdditionalDefaults() ?: Properties()
			properties["@background"] = toHex(colors.light.backgroundColor)
			properties["@foreground"] = toHex(colors.light.foregroundColor)
			return properties
		}
	}
}