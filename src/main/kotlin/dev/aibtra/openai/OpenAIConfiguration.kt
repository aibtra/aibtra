/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.diff.DiffManager
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
		val supportsSchemes: Boolean = false,
		val instructions: List<Instruction>,
		val diffConfig: DiffManager.Config
	) {

		@Serializable
		data class Name(val id: String, val title: String)
	}

	@Serializable
	data class Instruction(val role: Role, val text: String)

	@Suppress("unused")
	@Serializable
	enum class Role(val id: String) {
		USER("user"), SYSTEM("system"), ASSISTANT("assistant")
	}

	fun profile(id: String): Profile? {
		return profiles.find { it.name.id == id }
	}

	fun currentProfile(): Profile {
		return profiles.find { it.name.id == defaultProfileId } ?: PROOFREAD
	}

	companion object : ConfigurationFactory<OpenAIConfiguration> {
		private const val PROOFREAD_ID = "proofread"
		const val CONTENT_KEYWORD = "CONTENT"
		private const val CONTENT_MACRO = "\${${CONTENT_KEYWORD}}"
		private const val MODEL_4O = "gpt-4o"

		private val PROOFREAD = Profile(
			Profile.Name(PROOFREAD_ID, "Proofread (GPT-4o)"),
			MODEL_4O,
			true,
			listOf(
				Instruction(
					Role.USER, "Correct typos and grammar in the markdown following " +
									"AND stay as close as possible to the original " +
									"AND do not change the markdown structure " +
									"AND preserve the detected language:"
				),
				Instruction(Role.USER, CONTENT_MACRO)
			),
			DiffManager.Config(true, false)
		)

		private val IMPROVE = Profile(
			Profile.Name("improve", "Improve Text (GPT-4o)"),
			MODEL_4O,
			true,
			listOf(
				Instruction(
					Role.USER, "Proofread " +
									"AND improve wording, but stay close to the original, only apply changes to quite uncommon wording " +
									"AND do not change the markdown structure or indentation or other special symbols " +
									"AND preserve the detected language:"
				),
				Instruction(Role.USER, CONTENT_MACRO)
			),
			DiffManager.Config(true, false)
		)

		private val TO_STANDARD_ENGLISH = Profile(
			Profile.Name("to-standard-english", "To Standard English (GPT-4o)"),
			MODEL_4O,
			true,
			listOf(
				Instruction(
					Role.USER, "Rewrite to Standard English " +
									"BUT stay as close as possible to the original:"
				),
				Instruction(Role.USER, CONTENT_MACRO)
			),
			DiffManager.Config(true, false)
		)

		override fun name(): String = "openai"

		override fun default(): OpenAIConfiguration = OpenAIConfiguration()

		fun replaceProfile(originalConfig: OpenAIConfiguration, targetProfile: Profile, change: (Profile) -> Profile): OpenAIConfiguration {
			return originalConfig.copy(profiles = originalConfig.profiles.map { profile ->
				if (profile === targetProfile) {
					change(profile)
				}
				else {
					profile
				}
			})
		}
	}
}