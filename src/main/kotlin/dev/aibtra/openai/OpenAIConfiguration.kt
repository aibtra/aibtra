/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.core.WorkingMode
import dev.aibtra.diff.DiffManager
import kotlinx.serialization.Serializable

@Serializable
data class OpenAIConfiguration(
	val apiToken: String? = null,
	val profiles: List<Profile> = listOf(PROOFREAD, IMPROVE, TO_STANDARD_ENGLISH),
	val workingModeToDefaultProfileId: Map<WorkingMode, String> = WORKING_MODE_TO_DEFAULT_PROFILE_ID
) {

	@Serializable
	data class Profile(
		val name: Name,
		val model: String,
		val supportsSchemes: Boolean = false,
		val instructions: List<Instruction>,
		val responseType: ResponseType,
		val diffConfig: DiffManager.Config,
		val submitOnInvocation: Boolean = false,
		val submitOnProfileChange: Boolean = false,
		val wordWrap: Boolean = false
	) {

		fun supportsSelection(): Boolean {
			for (instruction in instructions) {
				if (instruction.text.contains(SELECTION_MACRO)) {
					return true
				}
			}

			return false
		}

		@Serializable
		data class Name(val id: String, val title: String)
	}

	enum class InstructionMode(private val matchSelection : Boolean, private val matchFull: Boolean) {
		SELECTION_ONLY(true, false), FULL_ONLY(false, true), ANY(true, true);

		fun matches(selectionMode: Boolean) : Boolean {
			return selectionMode && matchSelection || !selectionMode && matchFull
		}
	}

	@Serializable
	data class Instruction(val role: Role, val text: String, val mode: InstructionMode = InstructionMode.ANY)

	@Suppress("unused")
	@Serializable
	enum class Role(val id: String) {
		USER("user"), SYSTEM("system"), ASSISTANT("assistant")
	}

	@Serializable
	enum class ResponseType {
		CONTENT, SELECTION
	}

	fun profile(id: String): Profile? {
		return profiles.find { it.name.id == id }
	}

	fun currentProfile(workingMode: WorkingMode): Profile {
		return profiles.find {
			it.name.id == workingModeToDefaultProfileId.getOrDefault(workingMode, WORKING_MODE_TO_DEFAULT_PROFILE_ID[workingMode])
		} ?: PROOFREAD
	}

	companion object : ConfigurationFactory<OpenAIConfiguration> {
		private const val PROOFREAD_ID = "proofread"
		const val CONTENT_KEYWORD = "CONTENT"
		const val SELECTION_KEYWORD = "SELECTION"
		private const val CONTENT_MACRO = "\${${CONTENT_KEYWORD}}"
		private const val SELECTION_MACRO = "\${${SELECTION_KEYWORD}}"
		private const val MODEL_4O = "gpt-4o"
		private val WORKING_MODE_TO_DEFAULT_PROFILE_ID = mapOf(
			WorkingMode.clipboard to PROOFREAD_ID,
			WorkingMode.file to PROOFREAD_ID,
			WorkingMode.open to PROOFREAD_ID
		)

		private val PROOFREAD = Profile(
			Profile.Name(PROOFREAD_ID, "Proofread (GPT-4o)"),
			MODEL_4O,
			true,
			listOf(
				Instruction(
					Role.USER, "Correct typos and grammar in the markdown following " +
									"AND stay as close as possible to the original " +
									"AND do not change the markdown structure " +
									"AND preserve the detected language " +
									"AND do not include additional comments in the response, but purely the correction:"
				),
				Instruction(Role.USER, SELECTION_MACRO)
			),
			ResponseType.SELECTION,
			DiffManager.Config(true, false),
			wordWrap = true
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
									"AND preserve the detected language " +
									"AND do not include additional comments in the response, but purely the correction:"
				),
				Instruction(Role.USER, SELECTION_MACRO)
			),
			ResponseType.SELECTION,
			DiffManager.Config(true, false),
			wordWrap = true
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
				Instruction(Role.USER, SELECTION_MACRO)
			),
			ResponseType.SELECTION,
			DiffManager.Config(true, false),
			wordWrap = true
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