/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.text

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable

@Serializable
data class Schemes(
	val list: List<Scheme> = listOf(DEFAULT, EMAIL),
	val currentName: String = DEFAULT_NAME
) {
	fun current(): Scheme {
		return list.find { it.name == currentName } ?: DEFAULT
	}

	@Serializable
	data class Scheme(
		val name: String,
		val textNormalizerConfig: TextNormalizer.Config
	)

	companion object : ConfigurationFactory<Schemes> {
		const val DEFAULT_NAME = "Default"

		private val DEFAULT = Scheme(DEFAULT_NAME, TextNormalizer.Config(false, true, false, false, Int.MAX_VALUE))

		private val EMAIL = Scheme("Email", TextNormalizer.Config(true, true, true, true, 72))

		override fun name(): String = "schemes"

		override fun default(): Schemes {
			return Schemes()
		}
	}
}