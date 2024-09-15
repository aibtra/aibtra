/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(GuiConfiguration.MonospacedFontSerializer::class)

package dev.aibtra.main.frame

import com.formdev.flatlaf.util.SystemInfo
import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.gui.Ui
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.awt.Font
import java.awt.SystemTray
import javax.swing.JTextArea

@Serializable
data class GuiConfiguration(
	@SerialName("fonts.2") val fonts: Fonts = Fonts(),
	val darkTheme: Boolean = true, // Dark mode is in general preferred by programmers: https://css-tricks.com/poll-results-light-on-dark-is-preferred/
	val systemTray: Boolean = true,
	val hotkeyEnabled: Boolean = false,
	val pasteOnClose: Boolean = false,
	val submitOnInvocation: Boolean = false,
	val submitOnProfileChange: Boolean = false
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
			return SystemInfo.isWindows
							// GlobalScreen may slow down machine significantly in Debug mode, e.g. after a break point has been hit:
							// the mouse cursor lags extremely behind.
							&& !Ui.isDebugging()
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