/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(GuiConfiguration.MonospacedFontSerializer::class)

package dev.aibtra.main.frame

import com.formdev.flatlaf.util.*
import dev.aibtra.configuration.*
import dev.aibtra.configuration.ConfigurationFactory.Companion.paths
import dev.aibtra.gui.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.awt.*
import javax.swing.*

@Serializable
data class GuiConfiguration(
	val fonts: Fonts = Fonts(),
	val darkTheme: Boolean = true, // Dark mode is in general preferred by programmers: https://css-tricks.com/poll-results-light-on-dark-is-preferred/
	val systemTray: Boolean = true,
	val hotkeyEnabled: Boolean = false,
	val pasteOnClose: Boolean = false,
	val lastOpenPath: String? = null,
	val setup: Boolean = false
) {
	@Serializable
	data class Fonts(val monospacedFont: Font = Font(Font.MONOSPACED, Font.PLAIN, DEFAULT_FONT_SIZE)) {
		companion object {
			private val textAreaFontSize = JTextArea().font.size

			val DEFAULT_FONT_SIZE = if (Ui.isHiDPI()) {
				textAreaFontSize
			}
			else {
				Math.max(textAreaFontSize, 13)
			}
		}
	}

	companion object : ConfigurationFactory<GuiConfiguration> {
		override fun name(): String {
			return "gui"
		}

		override fun default(): GuiConfiguration {
			return GuiConfiguration()
		}

		fun isSystemTraySupported(): Boolean {
			return SystemTray.isSupported()
					&& SystemInfo.isWindows // The tray works reliably only on Windows; for example, on macOS, double-clicking the application icon does not work
		}

		fun isHotkeySupported(): Boolean {
			if ("true" == paths.getProperty("startup.forceHotkey")) {
				return true
			}

			if (Ui.isDebugging()) {
				// GlobalScreen may slow down machine significantly in Debug mode, e.g. after a break point has been hit:
				// the mouse cursor lags extremely behind.
				return false
			}

			return SystemInfo.isWindows || SystemInfo.isLinux || SystemInfo.isMacOS
		}

		fun isPasteOnCloseSupported(): Boolean {
			// The Robot generally works on macOS; however, after closing the window, the previous window does not regain focus, causing the key presses to be sent to nowhere
			return SystemInfo.isWindows || SystemInfo.isLinux
		}
	}

	object MonospacedFontSerializer : KSerializer<Font> {
		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Font", PrimitiveKind.STRING)

		override fun serialize(encoder: Encoder, value: Font) {
			encoder.encodeInt(value.size)
		}

		override fun deserialize(decoder: Decoder): Font {
			return Font(Font.MONOSPACED, Font.PLAIN, decoder.decodeInt())
		}
	}
}