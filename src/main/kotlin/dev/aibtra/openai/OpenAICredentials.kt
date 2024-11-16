/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable

@Serializable
data class OpenAICredentials(
	val apiToken: String? = null
) {
	companion object : ConfigurationFactory<OpenAICredentials> {

		override fun name(): String = "openai-credentials"

		override fun default(): OpenAICredentials = OpenAICredentials()
	}
}

