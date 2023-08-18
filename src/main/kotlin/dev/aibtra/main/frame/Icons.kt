/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.gui.Icon
import dev.aibtra.gui.Icon.Companion.create
import java.io.IOException

object Icons {
	val ACCEPT = loadIcon("accept")
	val SUBMIT = loadIcon("submit")
	val COPY = loadIcon("copy")
	val PASTE = loadIcon("paste")
	val LOGO = loadIcon("logo")
	val SHOW_REMOVED = loadIcon("show-removed")
	val DARK_MODE = loadIcon("dark-mode")
	val STOP = loadIcon("stop")

	private fun loadIcon(name: String): Icon {
		try {
			val pathLight = "/images/$name-96-light.png"
			val pathDark = "/images/$name-96-dark.png"
			val lightResource = Icons::class.java.getResource(pathLight)
			val darkResource = Icons::class.java.getResource(pathDark)
			return if (lightResource != null && darkResource != null) {
				create(lightResource, darkResource)
			}
			else {
				val pathBoth = "/images/$name-96.png"
				val bothResource = requireNotNull(Icons::class.java.getResource(pathBoth))
				create(bothResource, bothResource)
			}
		} catch (e: IOException) {
			throw RuntimeException(e)
		}
	}
}