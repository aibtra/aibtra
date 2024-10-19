/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(OpenAIConfiguration.MainSerializer::class)

package dev.aibtra.openai

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.core.WorkingMode
import dev.aibtra.diff.DiffManager
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.security.MessageDigest
import kotlin.jvm.optionals.getOrNull

@Serializable
data class OpenAIConfiguration(
	val apiToken: String? = null,
	val profiles: List<Profile> = DEFAULT_PROFILES,
	val workingModeToDefaultProfileId: Map<WorkingMode, String> = WORKING_MODE_TO_DEFAULT_PROFILE_ID,
	val lastCommands: List<String> = listOf(),
	val profileIdToHash: Map<String, String> = createProfileIdToHash(DEFAULT_PROFILES)
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

		fun toHashString(): String {
			// A reminder to not overwrite toString()
			return toString()
		}

		@Serializable
		data class Name(val id: String, val title: String)
	}

	enum class InstructionMode(private val matchSelection: Boolean, private val matchFull: Boolean) {
		SELECTION_ONLY(true, false), FULL_ONLY(false, true), ANY(true, true);

		fun matches(selectionMode: Boolean): Boolean {
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
		CONTENT, SELECTION, SELECTION_DIFF, SELECTION_JSON
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
			Profile.Name(CODE_REFINEMENT_ID, "Code refinement (o1-mini)"),
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
					"Focus only on this part of the file and apply changes only to this part:",
					InstructionMode.SELECTION_ONLY
				),
				Instruction(
					Role.USER,
					SELECTION_MACRO,
					InstructionMode.SELECTION_ONLY
				),
				Instruction(
					Role.USER,
					"Apply these changes:\n\n$COMMAND_MACRO"
				),
				Instruction(
					Role.USER,
					"Send back only the entire modified file. Do not include any additional comments.",
					InstructionMode.FULL_ONLY
				),
				Instruction(
					Role.USER,
					"""
						|Send back only the changed part of the file ("new") and which exact part to replace ("old", including the line number where the old block starts). Use following JSON format for your result:
						|{
						|  old: "..."
						|  oldLineStart: ...
						|  new: "..."
						|}""".trimMargin(),
					InstructionMode.SELECTION_ONLY
				)
			),
			ResponseType.SELECTION_JSON,
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

		private val DEFAULT_PROFILES = listOf(PROOFREAD, IMPROVE, TO_STANDARD_ENGLISH, CODE_REFINEMENT, GENERIC_COMMAND)

		override fun name(): String = "openai"

		override fun default(): OpenAIConfiguration = OpenAIConfiguration()

		override fun createSerializer(): KSerializer<OpenAIConfiguration> {
			return MainSerializer
		}

		fun getCommandInstructions(profile: Profile): List<String>? {
			val instructions = profile.instructions.stream().filter { i -> i.text.contains(COMMAND_MACRO) }.findAny().getOrNull()
			return instructions?.text?.split(COMMAND_MACRO)
		}

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

		fun createProfileIdToHash(profiles: List<Profile>): Map<String, String> {
			return profiles.associate { it.name.id to createProfileHash(it) }
		}

		private fun createProfileHash(profile: Profile): String {
			require(profile::class.isData)

			val bytes = profile.toHashString().toByteArray()
			val digest = MessageDigest.getInstance("SHA-256")
			val hashBytes = digest.digest(bytes)
			return hashBytes.joinToString("") { "%02x".format(it) }
		}
	}

	object MainSerializer : KSerializer<OpenAIConfiguration> {
		override val descriptor: SerialDescriptor = serializer().descriptor

		override fun deserialize(decoder: Decoder): OpenAIConfiguration {
			var configuration = serializer().deserialize(decoder)
			val idToHashDefault = HashMap(createProfileIdToHash(DEFAULT_PROFILES))
			for (profile in configuration.profiles) {
				val id = profile.name.id
				val expectedHash = configuration.profileIdToHash[id]
				val defaultHash = idToHashDefault.remove(id)
				if (expectedHash != createProfileHash(profile)) {
					continue
				}

				if (defaultHash != expectedHash) {
					val defaultProfile = DEFAULT_PROFILES.find { it.name.id == id }
					configuration = defaultProfile?.let {
						val replacedConfiguration = replaceProfile(configuration, profile) { _ -> it }
						updateHash(replacedConfiguration, it)
					} ?: run {
						configuration = removeProfile(configuration, profile)
						removeHash(configuration, id)
					}
				}
			}

			for (id in idToHashDefault.keys.sorted()) {
				val defaultProfile = requireNotNull(DEFAULT_PROFILES.find { it.name.id == id })
				val replacedConfiguration = addProfile(configuration, defaultProfile)
				configuration = updateHash(replacedConfiguration, defaultProfile)
			}

			return configuration
		}

		override fun serialize(encoder: Encoder, value: OpenAIConfiguration) {
			serializer().serialize(encoder, value)
		}

		private fun addProfile(originalConfig: OpenAIConfiguration, targetProfile: Profile): OpenAIConfiguration {
			return originalConfig.copy(profiles = originalConfig.profiles + targetProfile)
		}

		private fun removeProfile(originalConfig: OpenAIConfiguration, targetProfile: Profile): OpenAIConfiguration {
			return originalConfig.copy(profiles = originalConfig.profiles.filter { it !== targetProfile })
		}

		private fun updateHash(originalConfig: OpenAIConfiguration, profile: Profile): OpenAIConfiguration {
			val id = profile.name.id
			val hash = createProfileHash(profile)
			val replacedProfileIdToHash: Map<String, String> = originalConfig.profileIdToHash + (id to hash)
			return originalConfig.copy(profileIdToHash = replacedProfileIdToHash)
		}

		private fun removeHash(originalConfig: OpenAIConfiguration, profileId: String): OpenAIConfiguration {
			val replacedProfileIdToHash: Map<String, String> = originalConfig.profileIdToHash - profileId
			return originalConfig.copy(profileIdToHash = replacedProfileIdToHash)
		}
	}
}

