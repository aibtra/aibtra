/*
 *
 *  * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.gui

import com.formdev.flatlaf.util.*
import com.github.kwhat.jnativehook.*
import com.github.kwhat.jnativehook.keyboard.*
import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.frame.*
import java.awt.event.*

class HotkeyListener(
	private val configurationProvider: ConfigurationProvider,
	private val frameManager: FrameManager,
) {
	private var nativeKeyListener: NativeKeyListener? = null
	private var runnable: Runnable? = null

	fun configure(runnable: Runnable): Boolean  {
		if (!GuiConfiguration.isHotkeySupported()) {
			this.runnable = null
			return false // For debugging, we still want to get "true" returned, if configured.
		}

		this.runnable = runnable
		return update()
	}

	fun update() : Boolean {
		val hotkeyEnabled = configurationProvider.get(GuiConfiguration).hotkeyEnabled
		nativeKeyListener = if (hotkeyEnabled && runnable != null) {
			nativeKeyListener ?: createKeyListener(runnable).also {
				LOG.info("Hotkey enabled")

				if (!GlobalScreen.isNativeHookRegistered()) {
					try {
						GlobalScreen.registerNativeHook()
					} catch (ex: NativeHookException) {
						LOG.error(ex.message ?: "", ex)
						if (SystemInfo.isMacOS) {
							// Ignore, the operating system will display an appropriate dialog. If we were to display a dialog ourselves, it would cause the operating system's dialog to be pushed to the background.
						}
						else {
							showWarningDialog("Failed to register hotkey.\n\nSometimes, security settings may prevent registration of the hotkey.")
						}

						configurationProvider.change(GuiConfiguration) { config ->
							config.copy(hotkeyEnabled = false)
						}
						return false
					}
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

		return hotkeyEnabled
	}

	private fun showWarningDialog(message: String) {
		frameManager.getFrame()?.let {
			Dialogs.showWarning("Hotkey", message, it.dialogDisplayer)
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
				if (keyEvent.keyCode == CTRL_KEY_CODE) {
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

		val CTRL_KEY_CODE = if (SystemInfo.isMacOS) KeyEvent.VK_META else KeyEvent.VK_CONTROL

		val ACCELERATOR_DESCRIPTION = "${if (SystemInfo.isMacOS) "Cmd" else "Ctrl"}-C-C"
	}
}