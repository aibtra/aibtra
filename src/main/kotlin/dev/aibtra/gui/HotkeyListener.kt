/*
 *
 *  * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.gui

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.keyboard.SwingKeyAdapter
import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.core.Logger
import dev.aibtra.main.frame.GuiConfiguration
import java.awt.event.KeyEvent

class HotkeyListener(
	private val configurationProvider: ConfigurationProvider,
) {
	private var nativeKeyListener: NativeKeyListener? = null
	private var runnable: Runnable? = null

	fun configure(runnable: Runnable): Boolean  {
		val guiConfiguration = configurationProvider.get(GuiConfiguration)
		val hotkeyEnabled = guiConfiguration.hotkeyEnabled
		if (!GuiConfiguration.isHotkeySupported()) {
			this.runnable = null
			return hotkeyEnabled // For debugging, we still want to get "true" returned, if configured.
		}

		this.runnable = runnable
		update()
		return hotkeyEnabled
	}

	fun update() {
		val guiConfiguration = configurationProvider.get(GuiConfiguration)
		nativeKeyListener = if (guiConfiguration.hotkeyEnabled && runnable != null) {
			nativeKeyListener ?: createKeyListener(runnable).also {
				LOG.info("Hotkey enabled")

				if (!GlobalScreen.isNativeHookRegistered()) {
					GlobalScreen.registerNativeHook()
				}
				GlobalScreen.addNativeKeyListener(it)
			}
		}
		else {
			nativeKeyListener?.let {
				LOG.info("Hotkey disabled")

				GlobalScreen.removeNativeKeyListener(it)
			}
			null
		}
	}

	private fun createKeyListener(runnable: Runnable?): SwingKeyAdapter? {
		if (runnable == null) {
			return null
		}

		return object : SwingKeyAdapter() {
			var count: Int = 0
			var control: Boolean = false

			override fun keyPressed(keyEvent: KeyEvent) {
				if (keyEvent.keyCode == KeyEvent.VK_CONTROL) {
					control = true
					count = 0
					return
				}

				if (control && keyEvent.keyCode == KeyEvent.VK_C) {
					count++
					if (count == 2) {
						LOG.info("Hotkey detected")

						count = 0

						Ui.runInEdt {
							runnable.run()
						}
					}
				}
				else {
					count = 0
				}
			}

			override fun keyReleased(keyEvent: KeyEvent) {
				if (control && keyEvent.keyCode == KeyEvent.VK_CONTROL) {
					control = false
				}
			}
		}
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}