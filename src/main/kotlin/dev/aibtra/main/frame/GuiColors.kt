/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(GuiColors.ColorSerializer::class)

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.awt.Color

@Serializable
data class GuiColors(
	@SerialName("light.4") val light: Colors = Colors.DEFAULTS_LIGHT,
	@SerialName("dark.4") val dark: Colors = Colors.DEFAULTS_DARK,
) {

	@Serializable
	data class Colors(
		val rawBackgroundModified: Color, val rawBackgroundAdded: Color, val rawBackgroundRemoved: Color, val rawBackgroundRemovedShadow: Color,
		val refBackgroundModified: Color, val refBackgroundAdded: Color, val refBackgroundRemoved: Color, val refBackgroundRemovedShadow: Color,
		val selectionColor: Color,
		val textColor: Color,
	) {
		companion object {
			private val LIGHT_GREEN = Color(0x90, 0xEE, 0x90)
			private val LIGHT_GREEN_SHADOW = Color(0x90, 0xEE, 0x90, SHADOW_ALPHA)
			private val LIGHT_RED = Color(0xFF, 0x80, 0x80)
			private val LIGHT_RED_SHADOW = Color(0xFF, 0x80, 0x80, SHADOW_ALPHA)
			private val LIGHT_SELECTION = Color(0xCC, 0xCC, 0xCC)
			private val LIGHT_TEXT = Color(0x10, 0x10, 0x10)
			private val DARK_GREEN = Color(0x25, 0xA6, 0x25)
			private val DARK_GREEN_SHADOW = Color(0x25, 0xA6, 0x25, SHADOW_ALPHA)
			private val DARK_RED = Color(0xC6, 0x28, 0x28)
			private val DARK_RED_SHADOW = Color(0xC6, 0x28, 0x28, SHADOW_ALPHA)
			private val DARK_SELECTION = Color(0x70, 0x70, 0x70)
			private val DARK_TEXT = Color(0xCC, 0xCC, 0xCC)

			val DEFAULTS_LIGHT = Colors(LIGHT_RED, LIGHT_RED, LIGHT_GREEN, LIGHT_GREEN_SHADOW, LIGHT_RED, LIGHT_GREEN, LIGHT_RED, LIGHT_RED_SHADOW, LIGHT_SELECTION, LIGHT_TEXT)
			val DEFAULTS_DARK = Colors(DARK_RED, DARK_RED, DARK_GREEN, DARK_GREEN_SHADOW, DARK_RED, DARK_GREEN, DARK_RED, DARK_RED_SHADOW, DARK_SELECTION, DARK_TEXT)
		}
	}

	companion object : ConfigurationFactory<GuiColors> {
		private val SHADOW_ALPHA = 0x5F

		override fun name(): String {
			return "colors"
		}

		override fun default(): GuiColors {
			return GuiColors()
		}
	}

	object ColorSerializer : KSerializer<Color> {
		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

		override fun serialize(encoder: Encoder, value: Color) {
			encoder.encodeString(String.format("%02X%02X%02X%02X", value.red, value.green, value.blue, value.alpha))
		}

		override fun deserialize(decoder: Decoder): Color {
			val value = decoder.decodeString()
			val r = value.substring(0, 2).toInt(16)
			val g = value.substring(2, 4).toInt(16)
			val b = value.substring(4, 6).toInt(16)
			val a = value.substring(6, 8).toInt(16)
			return Color(r, g, b, a)
		}
	}
}