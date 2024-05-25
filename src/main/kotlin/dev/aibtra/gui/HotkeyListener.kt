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
import java.awt.AWTKeyStroke
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke


class HotkeyListener(
	private val configurationProvider: ConfigurationProvider,
) {
	private var nativeKeyListener: NativeKeyListener? = null
	private var runnable: Runnable? = null

	fun configure(runnable: Runnable) {
		this.runnable = runnable

		update()
	}

	fun isSupported(): Boolean {
		return GuiConfiguration.isHotkeySupported()
	}

	fun update() {
		val guiConfiguration = configurationProvider.get(GuiConfiguration)
		nativeKeyListener = if (guiConfiguration.hotkeyEnabled && runnable != null) {
			nativeKeyListener ?: createKeyListener(guiConfiguration, runnable).also {
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

	private fun createKeyListener(guiConfiguration: GuiConfiguration, runnable: Runnable?): SwingKeyAdapter? {
		if (runnable == null) {
			return null
		}

		val definition = guiConfiguration.hotkeyStroke
		val keyStroke = try {
			AWTKeyStroke.getAWTKeyStroke(definition)
		} catch (e: IllegalArgumentException) {
			LOG.error("Parsing hotkey '$definition' failed", e)
			return null
		}

		return object : SwingKeyAdapter() {
			var armed: MutableSet<Int>? = null

			override fun keyPressed(keyEvent: KeyEvent) {
				if (KeyStroke.getKeyStrokeForEvent(keyEvent) != keyStroke) {
					armed = null
					return
				}

				armed = HashSet<Int>().apply {
					add(keyEvent.keyCode)
					val modifiers: Int = keyEvent.modifiersEx
					if (modifiers and InputEvent.SHIFT_DOWN_MASK != 0) {
						add(KeyEvent.VK_SHIFT)
					}
					if (modifiers and InputEvent.CTRL_DOWN_MASK != 0) {
						add(KeyEvent.VK_CONTROL)
					}
					if (modifiers and InputEvent.META_DOWN_MASK != 0) {
						add(KeyEvent.VK_META)
					}
					if (modifiers and InputEvent.ALT_DOWN_MASK != 0) {
						add(KeyEvent.VK_ALT)
					}
					if (modifiers and InputEvent.ALT_GRAPH_DOWN_MASK != 0) {
						add(KeyEvent.VK_ALT_GRAPH)
					}
				}

				LOG.debug("Hotkey pressed: armed=$armed")
			}

			override fun keyReleased(keyEvent: KeyEvent) {
				// We listen to the release of the hotkey for several reasons:
				// - this prevents a large number of invocations if the hotkey combination remains pressed
				// - it ensures that no key is pressed at this point and thus the Robot can safely simulate Ctrl-C
				armed?.let {
					it.remove(keyEvent.keyCode)
					if (it.isEmpty()) {
						LOG.info("Hotkey detected")

						invokeRobot()
					}
				}
			}

			private fun invokeRobot() {
				Ui.runInEdt {
					val robot = Robot()
					robot.keyPress(KeyEvent.VK_CONTROL)
					robot.keyPress(KeyEvent.VK_C)
					robot.keyRelease(KeyEvent.VK_C)
					robot.keyRelease(KeyEvent.VK_CONTROL)
					runnable.run()
				}
			}
		}
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}