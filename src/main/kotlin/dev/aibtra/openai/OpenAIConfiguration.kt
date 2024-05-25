/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIConfiguration(
	val apiToken: String? = null,
	val profiles: List<Profile> = listOf(PROOFREAD, TO_STANDARD_ENGLISH),
	val defaultProfileName: String = PROOFREAD_TITLE
) {
	@Serializable
	class Profile(val name: String, val model: String, val instructions: String)

	companion object : ConfigurationFactory<OpenAIConfiguration> {
		const val PROOFREAD_TITLE = "Proofread (GPT-4o)"
		private const val MODEL_4O = "gpt-4o"

		private val PROOFREAD = Profile(
			PROOFREAD_TITLE, MODEL_4O,
			"Correct typos and grammar in the markdown following " +
					"AND stay as close as possible to the original " +
					"AND do not change the markdown structure " +
					"AND preserve the detected language:"
		)

		private val TO_STANDARD_ENGLISH = Profile(
			"To Standard English (GPT-4o)", MODEL_4O,
			"Rewrite to Standard English " +
					"BUT stay as close as possible to the original:"
		)

		override fun name(): String = "openai"

		override fun default(): OpenAIConfiguration = OpenAIConfiguration()
	}
}