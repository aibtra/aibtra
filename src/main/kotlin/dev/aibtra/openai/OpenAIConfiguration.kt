/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIConfiguration(
	val apiToken: String? = null,
	val profiles: List<Profile> = listOf(PROOFREAD, IMPROVE, TO_STANDARD_ENGLISH),
	val defaultProfileName: String = PROOFREAD_TITLE
) {
	@Serializable
	class Profile(
		val name: String,
		val model: String,
		val instructions: String,
		val supportsSchemes: Boolean = false
	)

	fun currentProfile(): Profile {
		return profiles.find { it.name == defaultProfileName } ?: PROOFREAD
	}

	companion object : ConfigurationFactory<OpenAIConfiguration> {
		const val PROOFREAD_TITLE = "Proofread (GPT-4o)"
		private const val MODEL_4O = "gpt-4o"

		private val PROOFREAD = Profile(
			PROOFREAD_TITLE, MODEL_4O,
			"Correct typos and grammar in the markdown following " +
							"AND stay as close as possible to the original " +
							"AND do not change the markdown structure " +
							"AND preserve the detected language:",
			true
		)

		private val IMPROVE = Profile(
			"Improve Text (GPT-4o)", MODEL_4O,
			"Proofread " +
							"AND improve wording, but stay close to the original, only apply changes to quite uncommon wording " +
							"AND do not change the markdown structure or indentation or other special symbols " +
							"AND preserve the detected language:",
			true
		)

		private val TO_STANDARD_ENGLISH = Profile(
			"To Standard English (GPT-4o)", MODEL_4O,
			"Rewrite to Standard English " +
							"BUT stay as close as possible to the original:",
			true
		)

		override fun name(): String = "openai"

		override fun default(): OpenAIConfiguration = OpenAIConfiguration()
	}
}