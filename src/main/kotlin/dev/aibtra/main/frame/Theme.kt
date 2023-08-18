/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import dev.aibtra.configuration.ConfigurationProvider
import java.awt.Frame
import javax.swing.SwingUtilities

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

		if (dark) {
			FlatDarkLaf.setup()
		}
		else {
			FlatLightLaf.setup()
		}

		for (frame in Frame.getFrames()) {
			SwingUtilities.updateComponentTreeUI(frame)
		}

		for (listener in listeners) {
			listener(this)
		}
	}
}