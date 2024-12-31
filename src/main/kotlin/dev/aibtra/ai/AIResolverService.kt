/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.ai

import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.parser.*
import com.vladsch.flexmark.util.ast.*
import com.vladsch.flexmark.util.data.*
import dev.aibtra.core.*
import dev.aibtra.core.DebugLog.*
import dev.aibtra.resolver.*
import org.json.simple.*
import java.io.*
import java.nio.file.*
import java.util.function.*
import kotlin.io.path.*

class AIResolverService(driver: AIDriver, apiToken: String, private val debugLog: DebugLog, val paths: ApplicationPaths) : AIService(driver, apiToken, debugLog) {
	fun request(approach: AIResolverConfiguration.Approach, snippets: ResolverSnippets, resolverPacket: ResolverPacket?, failureHandler: FailureHandler, callback: Callback) {
		var debugStep = 0

		val idToStepToDebugDetails = mutableMapOf<ResolverId, MutableMap<String, String>>()
		val merges = (resolverPacket as? ResolverPacketImpl)?.merges ?: run {
			val snippetsDebugString = snippets.joinToString("---\n\n") { it.toDebugString() }
			debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-conflicts", DebugLog.Level.INFO, snippetsDebugString, debugLog)

			val summarizeInput = createSummarizeInput(snippets, approach, idToStepToDebugDetails)
			debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-summarize-input", DebugLog.Level.INFO, JsonUtils.formatJson(summarizeInput.toJSONString()), debugLog)

			callback.startSummaries()
			val summarizeOutput = sendRequest(approach.model, summarizeInput, failureHandler, "summarize") ?: return
			debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-summarize-output", DebugLog.Level.INFO, summarizeOutput, debugLog)

			val summaries = extractSummaries(summarizeOutput, idToStepToDebugDetails)
			validateIds(summaries, snippets)?.let {
				debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-summarize-missing-conflicts", DebugLog.Level.INFO, it, debugLog)
			}

			if (!callback.handleSummaries(summarizeOutput)) {
				return
			}

			val mergeInput = createMergeInput(approach, summaries, idToStepToDebugDetails)
			debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-merge-input", DebugLog.Level.INFO, JsonUtils.formatJson(mergeInput.toJSONString()), debugLog)

			callback.startMerges()
			val mergeOutput = sendRequest(approach.model, mergeInput, failureHandler, "merge") ?: return
			debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-merge-output", DebugLog.Level.INFO, mergeOutput, debugLog)

			val merges = extractMerges(mergeOutput, idToStepToDebugDetails)
			validateIds(merges, snippets)?.let {
				debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-merge-missing-conflicts", DebugLog.Level.INFO, it, debugLog)
			}

			if (!callback.handleMerges(mergeOutput)) {
				return
			}

			merges
		}

		val resolveInput = createResolveInput(approach, snippets, merges, idToStepToDebugDetails)
		debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-resolve-input", DebugLog.Level.INFO, JsonUtils.formatJson(resolveInput.toJSONString()), debugLog)

		callback.startResolutions()
		val resolveOutput = sendRequest(approach.model, resolveInput, failureHandler, "resolve") ?: return
		debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-resolve-output", DebugLog.Level.INFO, resolveOutput, debugLog)

		val resolutions = extractResolutions(resolveOutput, idToStepToDebugDetails)
		validateIds(resolutions, snippets)?.let {
			debugLog(DEBUG_LOG_CATEGORY, "${++debugStep}-resolve-missing-conflicts", DebugLog.Level.INFO, it, debugLog)
		}

		val resolutionList = mutableListOf<ResolverResolution>()
		val idToResolution = resolutions.associateBy { it.id }
		for (snippet in snippets) {
			idToResolution[snippet.id]?.let {
				resolutionList.add(ResolverPatcher.apply(it.resolution, snippet, ResolverPatcher.FuzzyRange(5, 1)))
			}
		}

		val resolverResolutions = ResolverResolutions(snippets, resolutionList, ResolverPacketImpl(merges, idToStepToDebugDetails))
		callback.handleResolutions(resolverResolutions)

		callback.finish()
	}

	private fun createSummarizeInput(snippets: ResolverSnippets, approach: AIResolverConfiguration.Approach, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>): JSONArray {
		val input = JSONArray()
		val debugDetailsBuilderMain = StringBuilder()
		addMessage(approach.summarizeMainInstruction, input, debugDetailsBuilderMain)
		for (snippet in snippets) {
			val id = snippet.id
			val fileName = snippet.file.name
			val debugDetailsBuilder = StringBuilder(debugDetailsBuilderMain).append("\n\n")

			addMessage("Filename '$fileName', CONFLICT-ID '$id', CONFLICT:\n\n" + snippet.draft.join(false), AIRole.USER, input, debugDetailsBuilder)
			addMessage("Filename '$fileName', CONFLICT-ID '$id', BASE version:\n\n" + snippet.base.join(false), AIRole.USER, input, debugDetailsBuilder)
			addMessage("Filename '$fileName', CONFLICT-ID '$id', OURS version:\n\n" + snippet.ours.join(false), AIRole.USER, input, debugDetailsBuilder)
			addMessage("Filename '$fileName', CONFLICT-ID '$id', THEIRS version:\n\n" + snippet.theirs.join(false), AIRole.USER, input, debugDetailsBuilder)
			putDebugDetails(id, "1-summarize-input", debugDetailsBuilder, idToStepToDebugDetails)
		}
		return input
	}

	private fun createMergeInput(approach: AIResolverConfiguration.Approach, summaries: List<Summary>, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>): JSONArray {
		val input = JSONArray()
		val debugDetailsBuilderMain = StringBuilder()
		addMessage(approach.mergeMainInstruction, input, debugDetailsBuilderMain)
		for (summary in summaries) {
			val id = summary.id
			val debugDetailsBuilder = StringBuilder(debugDetailsBuilderMain).append("\n\n")

			addMessage("```\n${summary.content}\n```", AIRole.USER, input, debugDetailsBuilder)
			putDebugDetails(id, "2-merge-input", debugDetailsBuilder, idToStepToDebugDetails)
		}
		return input
	}

	private fun createResolveInput(approach: AIResolverConfiguration.Approach, snippets: ResolverSnippets, merges: List<Merge>, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>): JSONArray {
		val idToMerge = merges.associateBy { it.id }
		val input = JSONArray()
		val debugDetailsBuilderMain = StringBuilder()
		addMessage(approach.resolveMainInstruction, input, debugDetailsBuilderMain)
		for (snippet in snippets) {
			val id = snippet.id
			val fileName = snippet.file.name
			val builder = StringBuilder()
			val debugDetailsBuilder = StringBuilder(debugDetailsBuilderMain).append("\n\n")
			builder.append("CONFLICT-ID: $id\n")
			builder.append("FILENAME: $fileName\n")
			idToMerge[id]?.let {
				builder.append("HINTS:\n${it.instructions}\n")
			}
			builder.append("CONFLICT:\n```\n${snippet.draft.join(true)}```\n\n")
			builder.append("BASE version:\n```\n${snippet.base.join(true)}```\n\n")
			builder.append("OURS version:\n```\n${snippet.ours.join(true)}```\n\n")
			builder.append("THEIRS version:\n```\n${snippet.theirs.join(true)}```\n\n")
			addMessage(builder.toString(), AIRole.USER, input, debugDetailsBuilder)
			putDebugDetails(id, "3-resolve-input", debugDetailsBuilder, idToStepToDebugDetails)
		}
		return input
	}

	private fun extractSummaries(raw: String, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>): List<Summary> {
		val options = MutableDataSet()
		options.set(Parser.BLANK_LINES_IN_AST, true)

		val parser: Parser = Parser.builder(options).build()
		val node = parser.parse(raw)
		val summaries = mutableListOf<Summary>()
		object : NodeVisitor() {
			override fun processNode(node: Node, withChildren: Boolean, processor: BiConsumer<Node, Visitor<Node>>) {
				super.processNode(node, withChildren, processor)

				if (node is FencedCodeBlock) {
					val content = node.contentChars.toString()
					val regex = Regex("CONFLICT-ID:\\s+([a-f0-9-]+)")
					val matchResult = regex.find(content)
					matchResult?.groups?.get(1)?.value?.let {
						val id = ResolverId(it)
						summaries.add(Summary(id, content))
						putDebugDetails(id, "1-summarize-output", content, idToStepToDebugDetails)
					}
				}
			}
		}.visit(node)
		return summaries
	}

	private fun extractMerges(raw: String, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>): List<Merge> {
		val options = MutableDataSet()
		options.set(Parser.BLANK_LINES_IN_AST, true)

		val parser: Parser = Parser.builder(options).build()
		val node = parser.parse(raw)
		val merges = mutableListOf<Merge>()
		object : NodeVisitor() {
			override fun processNode(node: Node, withChildren: Boolean, processor: BiConsumer<Node, Visitor<Node>>) {
				super.processNode(node, withChildren, processor)

				if (node is FencedCodeBlock) {
					val content = node.contentChars.toString()
					val regex = Regex("CONFLICT-ID:\\s+([a-f0-9-]+)")
					val matchResult = regex.find(content)
					matchResult?.groups?.get(1)?.let {
						val instructionsIndex = content.indexOf(KEY_INSTRUCTIONS, it.range.last)
						if (instructionsIndex > 0) {
							val instructions = content.substring(instructionsIndex + KEY_INSTRUCTIONS.length)
							val id = ResolverId(it.value)
							merges.add(Merge(id, content, instructions))
							putDebugDetails(id, "2-merge-output", content, idToStepToDebugDetails)
						}
					}
				}
			}
		}.visit(node)
		return merges
	}

	private fun validateIds(ideds: List<Ided>, snippets: ResolverSnippets): String? {
		val summarizeIds = ideds.map { it.id }.toSet()
		val missingConflicts = snippets
			.filter { !summarizeIds.contains(it.id) }
			.toMutableSet()

		if (missingConflicts.isEmpty()) {
			return null
		}

		return missingConflicts.joinToString(separator = "\n\n") { it.toDebugString() }
	}

	private fun sendRequest(model: String, messages: JSONArray, failureHandler: FailureHandler, stepId: String): String? {
		paths.getProperty("resolver.simulate.$stepId")?.let {
			LOG.info("Simulating request for '$stepId'")
			return Path.of(it).readText()
		}

		LOG.info("Initiating '$stepId'")

		var text: String? = null
		var failurePair: Pair<IOException, Boolean>? = null
		request(model, messages, object : ResultHandler {
			override fun process(message: String) {
				text = message
			}
		}, object : FailureHandler {
			override fun process(failure: IOException, mightBeAuthentication: Boolean) {
				failurePair = Pair(failure, mightBeAuthentication)
			}
		})

		failurePair?.let {
			failureHandler.process(it.first, it.second)
			return null
		}

		paths.getProperty("resolver.output.$stepId")?.let { path ->
			text?.let {
				Path.of(path).writeText(it)
			}
		}

		return text
	}

	open class Ided(val id: ResolverId, val content: String)

	private class Summary(id: ResolverId, content: String) : Ided(id, content)

	private class Merge(id: ResolverId, content: String, val instructions: String) : Ided(id, content)

	class Resolution(id: ResolverId, content: String, val resolution: String) : Ided(id, content)

	interface Callback {
		fun startSummaries()

		fun handleSummaries(content: String): Boolean

		fun startMerges()

		fun handleMerges(content: String): Boolean

		fun startResolutions()

		fun handleResolutions(resolutions: ResolverResolutions)

		fun finish()
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)

		private const val DEBUG_LOG_CATEGORY = "resolver"
		private const val KEY_INSTRUCTIONS = "INSTRUCTIONS:"
		private const val KEY_RESOLUTION = "RESOLUTION:"

		fun addMessage(instruction: AIResolverConfiguration.Instruction, array: JSONArray, debugBuilder: StringBuilder) {
			debugBuilder.append("ROLE: ${instruction.role.id}\n")
			debugBuilder.append("${instruction.text}\n")
			debugBuilder.append("\n")
			array.add(createMessage(instruction))
		}

		fun addMessage(content: String, role: AIRole, array: JSONArray, debugBuilder: StringBuilder) {
			debugBuilder.append("ROLE: ${role.id}\n")
			debugBuilder.append("$content\n")
			debugBuilder.append("\n")
			array.add(createMessage(content, role))
		}

		fun extractResolutions(raw: String, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>): List<Resolution> {
			val options = MutableDataSet()
			options.set(Parser.BLANK_LINES_IN_AST, true)

			var input = raw
			input = input.replace(Regex("(?s)$KEY_RESOLUTION\n```\n(.*?)```"), "RESOLUTION:\n$1```")
			input = input.replace(Regex("(?s)$KEY_RESOLUTION\n```(\\w+)\n(.*?)```"), "RESOLUTION:\n$2```")

			val parser: Parser = Parser.builder(options).build()
			val node = parser.parse(input)
			val resolutions = mutableListOf<Resolution>()
			object : NodeVisitor() {
				override fun processNode(node: Node, withChildren: Boolean, processor: BiConsumer<Node, Visitor<Node>>) {
					super.processNode(node, withChildren, processor)

					if (node is FencedCodeBlock) {
						val content = node.contentChars.toString()
						val regex = Regex("CONFLICT-ID:\\s+([a-f0-9-]+)")
						val matchResult = regex.find(content)
						matchResult?.groups?.get(1)?.let {
							val textIndex = content.indexOf(KEY_RESOLUTION, it.range.last)
							if (textIndex > 0) {
								val text = content.substring(textIndex + KEY_RESOLUTION.length)
								val trimmed1 = if (text.startsWith("\n")) text.substring(1) else text
								val trimmed2 = if (trimmed1.endsWith("\n")) trimmed1.substring(0, trimmed1.length - 1) else text
								val id = ResolverId(it.value)
								resolutions.add(Resolution(id, content, trimmed2))
								putDebugDetails(id, "3-resolve-output", content, idToStepToDebugDetails)
							}
						}
					}
				}
			}.visit(node)
			return resolutions
		}

		fun debugLog(category: String, title: String, level: Level, text: String, debugLog: DebugLog) {
			debugLog.log(category, title, level, text)
		}
		
		private fun createMessage(instruction: AIResolverConfiguration.Instruction): JSONObject {
			return createMessage(instruction.text, instruction.role)
		}

		private fun putDebugDetails(id: ResolverId, step: String, details: StringBuilder, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>) {
			putDebugDetails(id, step, details.toString(), idToStepToDebugDetails)
		}

		private fun putDebugDetails(id: ResolverId, step: String, details: String, idToStepToDebugDetails: MutableMap<ResolverId, MutableMap<String, String>>) {
			idToStepToDebugDetails.computeIfAbsent(id) { mutableMapOf() }.compute(step) { _, old -> old?.let { it + "\n\n" + details } ?: details }
		}
	}

	private class ResolverPacketImpl(val merges: List<Merge>, override val idToStepToDebugDetails: Map<ResolverId, Map<String, String>>) : ResolverPacket
}