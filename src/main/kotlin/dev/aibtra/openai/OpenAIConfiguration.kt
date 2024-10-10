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
	val defaultProfileId: String = PROOFREAD_ID
) {

	@Serializable
	data class Profile(
		val name: Name,
		val model: String,
		val instructions: String,
		val supportsSchemes: Boolean = false
	) {

		@Serializable
		data class Name(val id: String, val title: String)
	}

	fun currentProfile(): Profile {
		return profiles.find { it.name.id == defaultProfileId } ?: PROOFREAD
	}

	companion object : ConfigurationFactory<OpenAIConfiguration> {
		private const val PROOFREAD_ID = "proofread"
		private const val MODEL_4O = "gpt-4o"

		private val PROOFREAD = Profile(
			Profile.Name(PROOFREAD_ID, "Proofread (GPT-4o)"),
			MODEL_4O,
			"Correct typos and grammar in the markdown following " +
							"AND stay as close as possible to the original " +
							"AND do not change the markdown structure " +
							"AND preserve the detected language:",
			true
		)

		private val IMPROVE = Profile(
			Profile.Name("improve", "Improve Text (GPT-4o)"),
			MODEL_4O,
			"Proofread " +
							"AND improve wording, but stay close to the original, only apply changes to quite uncommon wording " +
							"AND do not change the markdown structure or indentation or other special symbols " +
							"AND preserve the detected language:",
			true
		)

		private val TO_STANDARD_ENGLISH = Profile(
			Profile.Name("to-standard-english", "To Standard English (GPT-4o)"),
			MODEL_4O,
			"Rewrite to Standard English " +
							"BUT stay as close as possible to the original:",
			true
		)

		override fun name(): String = "openai"

		override fun default(): OpenAIConfiguration = OpenAIConfiguration()
	}
}