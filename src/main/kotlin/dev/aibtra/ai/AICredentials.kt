/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.ai

import dev.aibtra.configuration.*
import kotlinx.serialization.*

@Serializable
data class AICredentials(
	val providerToToken: Map<AIProvider, String> = mapOf()
) {
	companion object : ConfigurationFactory<AICredentials> {

		override fun name(): String = "ai-credentials"

		override fun default(): AICredentials = AICredentials()
	}
}

