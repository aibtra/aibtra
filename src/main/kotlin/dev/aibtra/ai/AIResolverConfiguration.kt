/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

@file:UseSerializers(AIRefinementConfiguration.MainSerializer::class)

package dev.aibtra.ai

import dev.aibtra.configuration.*
import kotlinx.serialization.*

@Serializable
data class AIResolverConfiguration(
	val profiles: List<Profile> = DEFAULT_PROFILES
) {
	@Serializable
	data class Profile(
		override val provider: AIProvider,
		override val name: AIProfile.Name,
		val approaches: List<Approach>
	) : AIProfile

	@Serializable
	data class Approach(
		val model: String,
		val summarizeMainInstruction: Instruction,
		val mergeMainInstruction: Instruction,
		val resolveMainInstruction: Instruction
	) {

		@Suppress("unused")
		fun toHashString(): String {
			// A reminder to not overwrite toString()
			return toString()
		}
	}

	@Serializable
	sealed interface Atom

	@Serializable
	data class Instruction(val role: AIRole, val text: String) : Atom

	fun profile(id: String): Profile? {
		return profiles.find { it.name.id == id }
	}

	companion object : ConfigurationFactory<AIResolverConfiguration> {
		private const val MODEL_O1_MINI = "o1-mini"

		private val THREE_STAGE_APPROACH = Approach(
			MODEL_O1_MINI,
			Instruction(
				AIRole.USER,
				"""
					For the following code snippets, there have been concurrent changes from BASE to OURS and from BASE to THEIRS.
					
					Provide a detailed analysis of these conflicts.
					
					Then, perform the following steps for each conflict:
					
					1. Analyze and explain the differences between BASE and OURS, and independently between BASE and THEIRS in natural language.
					
					2. Summarize these differences as a sequence of atomic operations, as you would instruct a software developer to transform BASE into OURS and to transform BASE into THEIRS. Ensure that the instructions for each conflict relate only to that specific conflict and not to others.
					
					3. Output this summary enclosed within a ```-block using the following format. Do not attempt to resolve conflicting instructions. Be sure to preserve the `CONFLICT-ID` and `FILENAME` exactly as provided:
					
					```
					CONFLICT-ID: <conflict-id>
					FILENAME: <filename>
					OURS:
					<list-of-atomic-operations-from-base-to-ours>
					THEIRS:
					<list-of-atomic-operations-from-base-to-theirs>
					```
				""".trimIndent()
			),
			Instruction(
				AIRole.USER,
				"""
					Below are multiple sets of high-level instructions for various source code conflicts, each containing conflicting directives labeled as OURS and THEIRS.
					
					Your task is to merge these conflicting directives into a single, coherent set of instructions for each conflict:
					
					1. Start by applying the OURS instructions.
					2. Then, incorporate the THEIRS instructions, adjusting them as necessary to account for any changes introduced by OURS.
					3. The final merged instructions should be actionable, enabling a developer to implement the required changes effectively.
					
					Output the combined instructions using the format below, preserving the conflict-id and filename exactly as provided and outputting every conflict in a separate ```-block:

					```
					CONFLICT-ID: <conflict-id>
					FILENAME: <filename>
					INSTRUCTIONS: <instructions>
					```
				""".trimIndent()
			),
			Instruction(
				AIRole.USER,
				"""
					Below is a set of conflicts between BASE and OURS, as well as between BASE and THEIRS.
					
					For each conflict, follow the instructions provided to resolve it:

					1. Resolve each conflict according to the corresponding HINTS:
					1.1 Ignore any HINTS that are unrelated to the core conflict.
					2. Maintain consistency between OURS and THEIRS where they are in sync:
					2.1 Preserve formatting, indentation and line breaks wherever both sides agree.
          3. Output the resolution in the format specified below:
					3.1. Use exactly the format shown.
					3.2. Ensure you preserve the `CONFLICT-ID` and `FILENAME` exactly as provided.
					3.3. Provide each resolution in a separate code block enclosed by triple backticks (```).
					3.4. Do not attempt to complete or modify any code beyond the conflict resolution.
					3.5. Preserve and report the non-conflicting areas exactly as they were.
							
					Format for each resolution:
					```
					CONFLICT-ID: <conflict-id>
					FILENAME: <filename>
					RESOLUTION:
					<conflict-resolution>
					```
				""".trimIndent()
			)
		)

		private val DEFAULT = Profile(
			AIProvider.OPENAI,
			AIProfile.Name("Default", "Default (o1-mini)"),
			listOf(THREE_STAGE_APPROACH)
		)

		private val DEFAULT_PROFILES = listOf(DEFAULT)

		override fun name(): String = "ai-resolver"

		override fun default(): AIResolverConfiguration = AIResolverConfiguration()
	}
}

