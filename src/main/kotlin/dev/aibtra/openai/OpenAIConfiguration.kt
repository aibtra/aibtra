/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIConfiguration(
	val apiToken: String? = null,
	val profiles: List<Profile> = listOf(CORRECTION_GPT_3_5_TURBO, CORRECTION_GPT_4, TO_STANDARD_ENGLISH),
	val defaultProfileName: String = PROOFREAD_3_5
) {
	@Serializable
	class Profile(val name: String, val model: String, val instructions: String)

	companion object : ConfigurationFactory<OpenAIConfiguration> {
		const val PROOFREAD_3_5 = "Proofread (GPT-3.5 Turbo)"

		private val CORRECTION_GPT_3_5_TURBO = Profile(
			PROOFREAD_3_5, "gpt-3.5-turbo",
			"Correct typos and grammar in the markdown following " +
					"AND stay as close as possible to the original " +
					"AND preserve block quotes and code blocks " +
					"AND preserve the detected language:"
		)

		private val CORRECTION_GPT_4 = Profile(
			"Proofread (GPT-4)", "gpt-4",
			"Correct typos and grammar in the markdown following " +
					"AND stay as close as possible to the original " +
					"AND do not change the markdown structure " +
					"AND preserve the detected language:"
		)

		private val TO_STANDARD_ENGLISH = Profile(
			"To Standard English (GPT-3.5 Turbo)", "gpt-3.5-turbo",
			"Rewrite to Standard English " +
					"BUT stay as close as possible to the original:"
		)

		override fun name(): String = "openai"

		override fun default(): OpenAIConfiguration = OpenAIConfiguration()
	}
}