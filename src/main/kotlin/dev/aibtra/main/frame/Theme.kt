/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.FlatLightLaf.setup
import dev.aibtra.configuration.ConfigurationProvider
import java.awt.Frame
import javax.swing.SwingUtilities
import javax.swing.UIDefaults


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
		if (dark) {
			FlatLaf.setup(object : FlatDarkLaf() {
				override fun getDefaults(): UIDefaults {
					val defaults = super.getDefaults()
					defaults["TextArea.background"] = defaults["Panel.background"]
					defaults["TextArea.foreground"] = guiColors.dark.textColor
					return defaults
				}
			})
		}
		else {
			setup(object : FlatLightLaf() {
				override fun getDefaults(): UIDefaults {
					val defaults = super.getDefaults()
					defaults["TextArea.foreground"] = guiColors.light.textColor
					return defaults
				}
			})
		}

		for (frame in Frame.getFrames()) {
			SwingUtilities.updateComponentTreeUI(frame)
		}

		for (listener in listeners) {
			listener(this)
		}
	}
}