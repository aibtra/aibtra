/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.core.WorkingMode
import dev.aibtra.diff.DiffManager
import kotlinx.serialization.Serializable
import kotlin.jvm.optionals.getOrNull

@Serializable
data class OpenAIConfiguration(
	val apiToken: String? = null,
	val profiles: List<Profile> = listOf(PROOFREAD, IMPROVE, TO_STANDARD_ENGLISH, CODE_REFINEMENT, GENERIC_COMMAND),
	val workingModeToDefaultProfileId: Map<WorkingMode, String> = WORKING_MODE_TO_DEFAULT_PROFILE_ID,
	val lastCommands: List<String> = listOf()
) {

	@Serializable
	data class Profile(
		val name: Name,
		val model: String,
		val streaming: Boolean,
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

	@Serializable
	data class Instruction(val role: Role, val text: String)

	@Suppress("unused")
	@Serializable
	enum class Role(val id: String) {
		USER("user"), SYSTEM("system"), ASSISTANT("assistant")
	}

	enum class ResponseType {
		CONTENT, SELECTION, DIFF, JSON
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
		private const val CODE_REFINEMENT_ID = "code-refinement"
		private const val GENERIC_COMMAND_ID = "generic-command"
		const val CONTENT_KEYWORD = "CONTENT"
		const val SELECTION_KEYWORD = "SELECTION"
		const val COMMAND_KEYWORD = "COMMAND"
		private const val CONTENT_MACRO = "\${${CONTENT_KEYWORD}}"
		private const val SELECTION_MACRO = "\${${SELECTION_KEYWORD}}"
		private const val COMMAND_MACRO = "\${${COMMAND_KEYWORD}}"
		private const val MODEL_4O = "gpt-4o"
		private const val MODEL_O1 = "o1-preview"
		private const val MODEL_O1_MINI = "o1-mini"
		private val WORKING_MODE_TO_DEFAULT_PROFILE_ID = mapOf(
			WorkingMode.clipboard to PROOFREAD_ID,
			WorkingMode.file to PROOFREAD_ID,
			WorkingMode.open to PROOFREAD_ID
		)

		private val PROOFREAD = Profile(
			Profile.Name(PROOFREAD_ID, "Proofread (GPT-4o)"),
			MODEL_4O,
			true,
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

		private val CODE_REFINEMENT = Profile(
			Profile.Name(CODE_REFINEMENT_ID, "Code refinement (o1-preview)"),
			MODEL_O1_MINI,
			false,
			false,
			listOf(
				Instruction(
					Role.USER,
					"I have following file:"
				),
				Instruction(
					Role.USER,
					CONTENT_MACRO
				),
				Instruction(
					Role.USER,
					"Focus only on this part of the file and apply changes only to this part:"
				),
				Instruction(
					Role.USER,
					SELECTION_MACRO
				),
				Instruction(
					Role.USER,
					"Apply these changes:\n\n$COMMAND_MACRO"
				),
				Instruction(
					Role.USER,
					"""
						|Send back only the changed part of the file ("new") and which exact part to replace ("old", including the line number where the old block starts). Use following JSON format for your result:
						|{
						|  old: "..."
						|  oldLineStart: ...
						|  new: "..."
						|}""".trimMargin()
				)
			),
			ResponseType.JSON,
			DiffManager.Config(false, false)
		)

		private val GENERIC_COMMAND = Profile(
			Profile.Name(GENERIC_COMMAND_ID, "Generic Command (o1-mini)"),
			MODEL_O1_MINI,
			false,
			false,
			listOf(
				Instruction(Role.USER, COMMAND_MACRO),
				Instruction(Role.USER, CONTENT_MACRO)
			),
			ResponseType.CONTENT,
			DiffManager.Config(false, false)
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

		fun getCommandInstructions(profile: Profile): List<String>? {
			val instructions = profile.instructions.stream().filter { i -> i.text.contains(COMMAND_MACRO) }.findAny().getOrNull()
			return instructions?.text?.split(COMMAND_MACRO)
		}
	}
}