/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(AIRefinementConfiguration.MainSerializer::class)

package dev.aibtra.ai

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.diff.*
import dev.aibtra.refiner.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.security.*
import kotlin.jvm.optionals.*

@Serializable
data class AIRefinementConfiguration(
	val profiles: List<Profile?> = DEFAULT_PROFILES,
	val workingModeToDefaultProfileId: Map<WorkingMode, String> = WORKING_MODE_TO_DEFAULT_PROFILE_ID,
	val lastCommands: List<String> = listOf(),
	val profileIdToHash: Map<String, String> = createProfileIdToHash(DEFAULT_PROFILES)
) {

	@Serializable
	data class Profile(
		override val provider: AIProvider,
		override val name: AIProfile.Name,
		val model: String,
		val streaming: Boolean,
		val supportsSchemes: Boolean = false,
		val mainInstructions: List<Instruction>,
		val followUpInstructions: List<Instruction>?,
		val responseType: ResponseType,
		val diffConfig: RefinerDiffManager.Config,
		val submitOnInvocation: Boolean = false,
		val submitOnProfileChange: Boolean = false,
		val wordWrap: Boolean = false,
		val accelerator: String? = null
	) : AIProfile {

		fun supportsSelection(): Boolean {
			for (instruction in mainInstructions) {
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
	}

	enum class InstructionMode(private val matchSelection: Boolean, private val matchFull: Boolean) {
		SELECTION_ONLY(true, false), FULL_ONLY(false, true), ANY(true, true);

		fun matches(selectionMode: Boolean): Boolean {
			return selectionMode && matchSelection || !selectionMode && matchFull
		}
	}

	@Serializable
	data class Instruction(val role: AIRole, val text: String, val mode: InstructionMode = InstructionMode.ANY)

	enum class ResponseType {
		CONTENT_AS_IS, CONTENT_FENCED;
	}

	fun profile(id: String): Profile? {
		return profiles.filterNotNull().find { it.name.id == id }
	}

	fun currentProfile(workingMode: WorkingMode): Profile {
		return profiles.filterNotNull().find {
			it.name.id == workingModeToDefaultProfileId.getOrDefault(workingMode, WORKING_MODE_TO_DEFAULT_PROFILE_ID[workingMode])
		} ?: PROOFREAD
	}

	companion object : ConfigurationFactory<AIRefinementConfiguration> {
		private const val PROOFREAD_ID = "proofread"
		private const val CODING_GPT_4O_ID = "code-adjustment"
		private const val CODING_O1_MINI_ID = "code-refinement"
		private const val CODING_CLAUDE_SONNET_ID = "code-claude-sonnet"
		private const val GENERIC_GPT_4O_ID = "generic-gpt-4o"
		private const val GENERIC_O3_MINI_ID = "generic-o3-mini"
		private const val GENERIC_CLAUDE_SONNET_ID = "generic-claude-sonnet"
		const val CONTENT_MACRO = "\${CONTENT}"
		const val SELECTION_MACRO = "\${SELECTION}"
		const val COMMAND_MACRO = "\${COMMAND}"
		const val FILENAME_MAIN = "{filename=\"main\"}"
		private const val MODEL_4O = "gpt-4o"
		private const val MODEL_O3_MINI = "o3-mini"
		private const val MODEL_CLAUDE_SONNET = "claude-3-5-sonnet-20241022"
		private val WORKING_MODE_TO_DEFAULT_PROFILE_ID = mapOf(
			WorkingMode.CLIPBOARD to PROOFREAD_ID,
			WorkingMode.FILE to PROOFREAD_ID,
			WorkingMode.OPEN to PROOFREAD_ID
		)

		private val PROOFREAD = Profile(
			AIProvider.OPENAI,
			AIProfile.Name(PROOFREAD_ID, "Proofread (GPT-4o)"),
			MODEL_4O,
			true,
			true,
			listOf(
				Instruction(
					AIRole.USER, "Correct typos and grammar in the markdown following " +
									"AND stay as close as possible to the original " +
									"AND do not change the markdown structure " +
									"AND preserve the detected language " +
									"AND do not include additional comments in the response, but purely the correction:"
				),
				Instruction(AIRole.USER, SELECTION_MACRO)
			),
			null,
			ResponseType.CONTENT_AS_IS,
			RefinerDiffManager.Config(true, false, DiffTokenizingMode.NONE, true),
			wordWrap = true,
			accelerator = "ctrl shift P"
		)

		private val IMPROVE = Profile(
			AIProvider.OPENAI,
			AIProfile.Name("improve", "Improve Text (GPT-4o)"),
			MODEL_4O,
			true,
			true,
			listOf(
				Instruction(
					AIRole.USER, "Proofread " +
									"AND improve wording, but stay close to the original, only apply changes to quite uncommon wording " +
									"AND do not change the markdown structure or indentation or other special symbols " +
									"AND preserve the detected language " +
									"AND do not include additional comments in the response, but purely the correction:"
				),
				Instruction(AIRole.USER, SELECTION_MACRO)
			),
			null,
			ResponseType.CONTENT_AS_IS,
			RefinerDiffManager.Config(true, false, DiffTokenizingMode.NONE, true),
			wordWrap = true,
			accelerator = "ctrl shift I"
		)

		private val TO_STANDARD_ENGLISH = Profile(
			AIProvider.OPENAI,
			AIProfile.Name("to-standard-english", "To Standard English (GPT-4o)"),
			MODEL_4O,
			true,
			true,
			listOf(
				Instruction(
					AIRole.USER, "Rewrite to Standard English " +
									"BUT stay as close as possible to the original:"
				),
				Instruction(AIRole.USER, SELECTION_MACRO)
			),
			null,
			ResponseType.CONTENT_AS_IS,
			RefinerDiffManager.Config(true, false, DiffTokenizingMode.NONE, true),
			wordWrap = true
		)

		private val CODING_GPT_4O = createCodeRefinementProfile(AIProvider.OPENAI, CODING_GPT_4O_ID, "Coding GPT-4o", MODEL_4O)

		private val CODING_O1_MINI = createCodeRefinementProfile(AIProvider.OPENAI, CODING_O1_MINI_ID, "Coding o3-mini", MODEL_O3_MINI, "ctrl shift R")

		private val CODING_CLAUDE_SONNET = createCodeRefinementProfile(AIProvider.ANTHROPIC, CODING_CLAUDE_SONNET_ID, "Coding Claude Sonnet", MODEL_CLAUDE_SONNET)

		private val GENERIC_GPT_4O = createGenericProfile(AIProvider.OPENAI, GENERIC_GPT_4O_ID, "Generic GPT-4o", MODEL_4O)

		private val GENERIC_O1_MINI = createGenericProfile(AIProvider.OPENAI, GENERIC_O3_MINI_ID, "Generic o3-mini", MODEL_O3_MINI)

		private val GENERIC_CLAUDE_SONNET = createGenericProfile(AIProvider.ANTHROPIC, GENERIC_CLAUDE_SONNET_ID, "Generic Claude Sonnet", MODEL_CLAUDE_SONNET)

		private val DEFAULT_PROFILES = listOf(PROOFREAD, IMPROVE, TO_STANDARD_ENGLISH, null, CODING_GPT_4O, CODING_O1_MINI, CODING_CLAUDE_SONNET, null, GENERIC_GPT_4O, GENERIC_O1_MINI, GENERIC_CLAUDE_SONNET)

		override fun name(): String = "ai-refiner"

		override fun default(): AIRefinementConfiguration = AIRefinementConfiguration()

		override fun createSerializer(): KSerializer<AIRefinementConfiguration> {
			return MainSerializer
		}

		fun getCommandInstructions(profile: Profile, followUp: Boolean): List<String>? {
			val instructionsList = if (followUp) {
				profile.followUpInstructions ?: profile.mainInstructions
			}
			else {
				profile.mainInstructions
			}

			val instructions = instructionsList.stream().filter { i -> i.text.contains(COMMAND_MACRO) }.findAny().getOrNull()
			return instructions?.text?.split(COMMAND_MACRO)?.map {
				if (it.trim().startsWith("```")) "" else it
			}
		}

		fun replaceProfile(originalConfig: AIRefinementConfiguration, targetProfile: Profile, change: (Profile) -> Profile): AIRefinementConfiguration {
			return originalConfig.copy(profiles = originalConfig.profiles.map { profile ->
				if (profile === targetProfile) {
					change(profile)
				}
				else {
					profile
				}
			})
		}

		fun createProfileIdToHash(profiles: List<Profile?>): Map<String, String> {
			return profiles.filterNotNull().associate { it.name.id to createProfileHash(it) }
		}

		private fun createProfileHash(profile: Profile): String {
			require(profile::class.isData)

			val bytes = profile.toHashString().toByteArray()
			val digest = MessageDigest.getInstance("SHA-256")
			val hashBytes = digest.digest(bytes)
			return hashBytes.joinToString("") { "%02x".format(it) }
		}

		private fun createGenericProfile(provider: AIProvider, id: String, title: String, model: String): Profile {
			return Profile(
				provider,
				AIProfile.Name(id, title),
				model,
				true,
				false,
				listOf(
					Instruction(AIRole.USER, COMMAND_MACRO),
					Instruction(AIRole.USER, SELECTION_MACRO)
				),
				listOf(
					Instruction(AIRole.USER, COMMAND_MACRO),
					Instruction(AIRole.USER, SELECTION_MACRO)
				),
				ResponseType.CONTENT_AS_IS,
				RefinerDiffManager.Config(false, false, DiffTokenizingMode.NONE, false)
			)
		}

		private fun createCodeRefinementProfile(provider: AIProvider, id: String, title: String, model: String, accelerator: String? = null): Profile {
			return Profile(
				provider,
				AIProfile.Name(id, title),
				model,
				false,
				false,
				listOf(
					Instruction(
						AIRole.USER,
						"""
						|Your objective is to apply the specified changes to a source code file:
						|
						|1. Begin by providing a detailed reasoning process about the planned modifications, explaining why and how each change will be implemented.
						|2. Conclude your response with the updated file content enclosed in triple backticks (```) and be sure to include `$FILENAME_MAIN` as code block attribute.
						|2.1 Be sure to preserve the indentation of every line exactly as is.
						|
						|The changes to be applied are described below:
						|$COMMAND_MACRO
					""".trimMargin()
					),
					Instruction(
						AIRole.USER,
						"""
						|This is the file content:
						|
						|```
						|$CONTENT_MACRO
						|```
					""".trimMargin()
					),
					Instruction(
						AIRole.USER,
						"""
						|The given changes should be applied only to following portion of the file and only this modified portion should be sent back.
						|Do not touch other parts of the file.
						|
						|```
						|$SELECTION_MACRO
						|```
					""".trimMargin(),
						InstructionMode.SELECTION_ONLY
					)
				),
				listOf(
					Instruction(
						AIRole.USER,
						"""
						|Continue to refine the file by applying more changes. Conclude your response with the updated file content enclosed in triple backticks (```) and be sure to include `$FILENAME_MAIN` as code block attribute. The changes to be applied are described below:
						|
						|$COMMAND_MACRO
					""".trimMargin()
					),
					Instruction(
						AIRole.USER,
						// When switching back and forth between selecting and entire file, it's important to ensure that we will get sent back the entire file.
						"""
						|The following is the complete content of the file. Make the requested modifications and ensure the response includes the entire updated file content.
						|
						|```
						|$CONTENT_MACRO
						|```
					""".trimMargin(),
						InstructionMode.FULL_ONLY
					),
					Instruction(
						AIRole.USER,
						"""
						|The given changes should be applied only to following portion of the file and only this modified portion should be sent back.
						|Do not touch other parts of the file.
						|
						|```
						|$SELECTION_MACRO
						|```
					""".trimMargin(),
						InstructionMode.SELECTION_ONLY
					)
				),
				ResponseType.CONTENT_FENCED,
				RefinerDiffManager.Config(false, false, DiffTokenizingMode.ALPHANUMERIC, true),
				accelerator = accelerator
			)
		}
	}

	object MainSerializer : KSerializer<AIRefinementConfiguration> {
		override val descriptor: SerialDescriptor = serializer().descriptor

		override fun deserialize(decoder: Decoder): AIRefinementConfiguration {
			var configuration = serializer().deserialize(decoder)
			val idToHashDefault = HashMap(createProfileIdToHash(DEFAULT_PROFILES))
			for (profile in configuration.profiles.filterNotNull()) {
				val id = profile.name.id
				val expectedHash = configuration.profileIdToHash[id]
				val defaultHash = idToHashDefault.remove(id)
				if (expectedHash != createProfileHash(profile)) {
					continue
				}

				if (defaultHash != expectedHash) {
					val defaultProfile = DEFAULT_PROFILES.filterNotNull().find { it.name.id == id }
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
				val defaultProfile = requireNotNull(DEFAULT_PROFILES.filterNotNull().find { it.name.id == id })
				val replacedConfiguration = addProfile(configuration, defaultProfile)
				configuration = updateHash(replacedConfiguration, defaultProfile)
			}

			return configuration
		}

		override fun serialize(encoder: Encoder, value: AIRefinementConfiguration) {
			serializer().serialize(encoder, value)
		}

		private fun addProfile(originalConfig: AIRefinementConfiguration, targetProfile: Profile): AIRefinementConfiguration {
			return originalConfig.copy(profiles = originalConfig.profiles + targetProfile)
		}

		private fun removeProfile(originalConfig: AIRefinementConfiguration, targetProfile: Profile): AIRefinementConfiguration {
			return originalConfig.copy(profiles = originalConfig.profiles.filter { it !== targetProfile })
		}

		private fun updateHash(originalConfig: AIRefinementConfiguration, profile: Profile): AIRefinementConfiguration {
			val id = profile.name.id
			val hash = createProfileHash(profile)
			val replacedProfileIdToHash: Map<String, String> = originalConfig.profileIdToHash + (id to hash)
			return originalConfig.copy(profileIdToHash = replacedProfileIdToHash)
		}

		private fun removeHash(originalConfig: AIRefinementConfiguration, profileId: String): AIRefinementConfiguration {
			val replacedProfileIdToHash: Map<String, String> = originalConfig.profileIdToHash - profileId
			return originalConfig.copy(profileIdToHash = replacedProfileIdToHash)
		}
	}
}

