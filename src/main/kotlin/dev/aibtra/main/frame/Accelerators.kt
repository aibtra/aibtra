/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.core.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import javax.swing.KeyStroke

@Serializable
class Accelerators(private val actionIdToAccelerator: Map<String, @Serializable(with = KeyStrokeSerializer::class) KeyStroke?> = mapOf()) {

	fun get(actionId: String, default: KeyStroke?): KeyStroke? {
		return actionIdToAccelerator[actionId] ?: default
	}

	companion object : ConfigurationFactory<Accelerators> {
		override fun name(): String {
			return "accelerators"
		}

		override fun default(): Accelerators {
			return Accelerators()
		}
	}

	object KeyStrokeSerializer : KSerializer<KeyStroke?> {
		private val LOG = Logger.getLogger(this::class)

		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KeyStroke", PrimitiveKind.STRING)

		override fun serialize(encoder: Encoder, value: KeyStroke?) {
			encoder.encodeString(value.toString())
		}

		override fun deserialize(decoder: Decoder): KeyStroke? {
			val definition = decoder.decodeString()
			val keyStroke = KeyStroke.getKeyStroke(definition)
			if (keyStroke == null) {
				LOG.warn("Invalid key stroke definition '$definition'")
			}

			return keyStroke
		}
	}
}