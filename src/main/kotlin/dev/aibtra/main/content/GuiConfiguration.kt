/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(GuiConfiguration.MonospacedFontSerializer::class)

package dev.aibtra.main.content

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
	data class Fonts(val monospacedFont: Font = getDefaultMonospacedFont()) {
		companion object {
			private val editorFontSize = JTextArea().font.size

			val DEFAULT_FONT_SIZE = if (Ui.isHiDPI()) {
				editorFontSize
			}
			else {
				Math.max(editorFontSize, 13)
			}

			private fun getDefaultMonospacedFont(): Font {
				val fontName = when {
					SystemInfo.isWindows -> {
						listOf(
							"Cascadia Code",
							"JetBrains Mono",
							Font.MONOSPACED
						).firstOrNull { isFontAvailable(it) } ?: Font.MONOSPACED
					}

					else -> Font.MONOSPACED
				}
				return Font(fontName, Font.PLAIN, DEFAULT_FONT_SIZE)
			}

			private fun isFontAvailable(fontName: String): Boolean {
				return GraphicsEnvironment
					.getLocalGraphicsEnvironment()
					.availableFontFamilyNames
					.any { it.equals(fontName, ignoreCase = true) }
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
		override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Font") {
			element<String>("name")
			element<Int>("size")
		}

		override fun serialize(encoder: Encoder, value: Font) {
			encoder.encodeStructure(descriptor) {
				encodeStringElement(descriptor, 0, value.name)
				encodeIntElement(descriptor, 1, value.size)
			}
		}

		override fun deserialize(decoder: Decoder): Font {
			return decoder.decodeStructure(descriptor) {
				var name = Font.MONOSPACED
				var size = 12 // Default size

				while (true) {
					when (val index = decodeElementIndex(descriptor)) {
						0 -> name = decodeStringElement(descriptor, index)
						1 -> size = decodeIntElement(descriptor, index)
						CompositeDecoder.DECODE_DONE -> break
						else -> throw SerializationException("Unexpected index: $index")
					}
				}
				Font(name, Font.PLAIN, size)
			}
		}
	}
}