package dev.aibtra.refiner

import dev.aibtra.core.*
import dev.aibtra.diff.*
import dev.aibtra.gui.*
import dev.aibtra.text.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Runnable
import kotlinx.serialization.Serializable

class RefinerDiffManager(
	private val rawNormalizer: RawNormalizer,
	coroutineDispatcher: CoroutineDispatcher,
	mainScope: CoroutineScope,
	private val debugLog: DebugLog
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(coroutineDispatcher, mainScope)
	private val stateListeners = ArrayList<(State, State) -> Unit>()
	val scrollState = ScrollState()

	private var data: Data = Data(Input(FilteredText.Part.of(""), "", "", INITIAL_CONFIG, true, null), State(FilteredText.Part.of(""), listOf(), FilteredText.asIs(FilteredText.Part.of("")), "", listOf(), Diff.INITIAL, false), 0)

	val state: State
		get() = data.state

	fun updateRawText(raw: String, selection: IntRange?, rawConfig: Config? = null, normalization: Normalization = Normalization.AS_IS, callback: Runnable? = null): String {
		Ui.assertEdt()

		data.let {
			val (from, to) = selection?.let { s -> s.first to s.last + 1 } ?: (0 to raw.length)
			require(from <= to && to <= raw.length) { "from=${from};to=${to};raw=${raw}" }

			val part = FilteredText.Part(raw, from, to)
			val input = it.input
			val config = rawConfig ?: input.config
			if (input.raw.all == raw && input.raw == part && config == input.config && normalization == Normalization.AS_IS && callback == null) {
				return raw
			}

			// For the "initial" call, we are backing up raw to rawOrg,
			// for all subsequent calls we are reusing this backup.
			val rawOrg: String? = when (normalization) {
				Normalization.INITIALIZE -> raw
				Normalization.STOP -> null
				Normalization.AS_IS -> input.rawOrg
			}

			// We will apply the normalization as long as the raw text has not been manually changed.
			// As long as there is no manual change, the raw text will be equal to the normalized version of rawOrg.
			// If the raw text is not equal anymore, there must definitely have been a manual change.
			// On the other hand, we will only overlook a manual change if the user manually normalizes the raw text to exactly the same as the normalized version of rawOrg.
			// It is reasonable to continue normalizing after such kind of a change.
			val rawNormalized: String = rawNormalizer.normalize(raw)
			val rawOrgNormalized: String? = rawOrg?.let { rawNormalizer.normalize(rawOrg) }
			val (rawNew, rawOrgNew) = if (rawNormalized == rawOrgNormalized) {
				Pair(rawNormalized, rawOrg)
			}
			else {
				Pair(raw, null)
			}

			updateState(input.copy(raw = part, rawOrg = rawOrgNew, config = config, callback = callback), true, "updateRaw")
			scrollState.updateLeftText(raw)
			return rawNew
		}
	}

	fun updateRefText(ref: String, finished: Boolean) {
		Ui.assertEdt()

		data.let {
			if (it.input.ref == ref && it.input.finished == finished) {
				return
			}

			// Once starting the refinement, this will be no more the "initial" state, hence reset rawOrg
			updateState(it.input.copy(ref = ref, finished = finished), finished, if (finished) "updateRef" else null)
		}
	}

	fun updateInitial(): String? {
		Ui.assertEdt()

		data.let {
			val input = it.input
			val rawOrg = input.rawOrg ?: return null
			val normalized = rawNormalizer.normalize(rawOrg)
			updateState(input.copy(raw = FilteredText.Part.of(normalized), rawOrg = rawOrg), true, "updateInitial")
			return rawOrg
		}
	}

	fun setConfig(config: Config) {
		Ui.assertEdt()

		data.let {
			if (it.input.config == config) {
				return
			}

			updateState(it.input.copy(config = config), true, "setConfig")
		}
	}

	fun addStateListener(listener: (state: State, last: State) -> Unit) {
		Ui.assertEdt()

		stateListeners.add(listener)
	}

	fun removeStateListener(listener: (state: State, last: State) -> Unit) {
		Ui.assertEdt()

		stateListeners.remove(listener)
	}

	private fun updateState(input: Input, forceUpdate: Boolean, debugOperationName: String?) {
		LOG.debug("updateState (schedule): operationName=" + (debugOperationName ?: "<null>") + ", raw=" + input.raw.all.length + ", rawOrg=" + (input.rawOrg?.length ?: "<null>") + ", ref=" + input.ref.length + ", finished=" + input.finished + ", callback=" + input.callback + ", config=" + input.config)

		val dataState = data.state
		data = Data(input, dataState, data.sequenceId + 1)

		if (debugOperationName != null) {
			writeDebugFile(data.sequenceId, debugOperationName, "input-raw", input.raw.all, null)
			writeDebugFile(data.sequenceId, debugOperationName, "input-raw-selection", input.raw.extract, null)
			writeDebugFile(data.sequenceId, debugOperationName, "input-clean", data.state.filtered.clean.all, null)
			writeDebugFile(data.sequenceId, debugOperationName, "input-ref", input.ref, null)
		}

		sequentialRunner.schedule(object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				LOG.debug("updateState (run): operationName=" + (debugOperationName ?: "<null>") + ", raw=" + input.raw.all.length + ", rawOrg=" + (input.rawOrg?.length ?: "<null>") + ", ref=" + input.ref.length + ", finished=" + input.finished + ", callback=" + input.callback + ", config=" + input.config)

				val raw = input.raw
				val ref = input.ref
				val config = input.config
				val finished = input.finished
				val selection = input.raw.isPart()
				val diff = DiffExtender(config.tokenizingMode, enabled = config.enabled).extend(raw.all, ref, state.diff, finished)

				val (rawFormatted, rawChars) = DiffFormatter(DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED).format(diff)
				require(rawFormatted.length == raw.all.length)

				val filtered: FilteredText = if (config.filterMarkdown) {
					FilteredText.filter(raw)
				}
				else {
					FilteredText.asIs(raw)
				}

				val mode: DiffFormatter.Mode = if (config.showRefBeforeAndAfter) {
					DiffFormatter.Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED
				}
				else {
					DiffFormatter.Mode.KEEP_REF_FOR_MODIFIED
				}
				val (refFormatted, refChars) = DiffFormatter(mode).format(diff)
				val state = State(raw, rawChars, filtered, refFormatted, refChars, diff, selection)
				callback {
					Ui.assertEdt()

					val latestData = this@RefinerDiffManager.data
					val latestInput = latestData.input
					val data = Data(latestInput.copy(callback = null), state, latestData.sequenceId + 1)
					val latestState = latestData.state
					this@RefinerDiffManager.data = data
					latestInput.callback?.run()

					if (debugOperationName != null) {
						writeDebugFile(data.sequenceId, debugOperationName, "state-raw", data.state.diff.raw, data.state.rawChars)
						writeDebugFile(data.sequenceId, debugOperationName, "state-clean", data.state.filtered.clean.all, null)
						writeDebugFile(data.sequenceId, debugOperationName, "state-ref", data.state.refFormatted, data.state.refChars)
					}

					stateListeners.toList().forEach { it(state, latestState) }

					scrollState.updateDiffBlocks(state.diff.blocks)
					if (latestState.refFormatted != state.refFormatted) {
						scrollState.updateRightText(refFormatted)
					}
				}
			}
		}, forceUpdate)
	}

	private fun writeDebugFile(sequenceId: Int, operationName: String, type: String, text: String, diffChars: List<DiffChar>?) {
		debugLog.run("diffManager", "${sequenceId}-${operationName}-${type}", DebugLog.Level.DEBUG) { log, active ->
			if (!active) {
				return@run
			}

			var textBuilder = StringBuilder()
			var diffBuilder = StringBuilder()
			diffChars?.let {
				require(text.length == it.size)
			}

			for (i in text.indices) {
				val ch = text[i]
				textBuilder.append(ch)
				diffBuilder.append(diffChars?.let { it[i].kind.char } ?: ' ')
				if (ch == '\n') {
					log.println(textBuilder.toString())
					log.println(diffBuilder.toString())

					textBuilder = StringBuilder()
					diffBuilder = StringBuilder()
				}
			}

			log.println(textBuilder.toString())
			log.println(diffBuilder.toString())
		}
	}

	class State(val rawText: FilteredText.Part, val rawChars: List<DiffChar>, val filtered: FilteredText, val refFormatted: String, val refChars: List<DiffChar>, val diff: Diff, val selection: Boolean)

	private data class Input(val raw: FilteredText.Part, val rawOrg: String?, val ref: String, val config: Config, val finished: Boolean, val callback: Runnable?)

	private class Data(val input: Input, val state: State, val sequenceId: Int)

	companion object {
		private val LOG = Logger.getLogger(this::class)
		val INITIAL_CONFIG = Config(false, false, DiffTokenizingMode.NONE, true)

		fun getSelectedBlocksFromRef(state: State, range: IntRange): List<DiffBlock> {
			require(range.first >= 0 && range.last < state.refFormatted.length)

			val refFrom = state.refChars[range.first].posRef
			val refTo = state.refChars[range.last].posRef
			require(refFrom >= 0 && refTo < state.diff.ref.length)

			val selected = ArrayList<DiffBlock>()
			for (block in state.diff.blocks) {
				if (block.refFrom == block.refTo
					&& refFrom <= block.refFrom
					&& block.refFrom <= refTo + 1
				) {
					selected.add(block)
				}
				else if (refFrom < block.refTo && block.refFrom <= refTo) {
					selected.add(block)
				}
			}

			return selected
		}
	}

	@Serializable
	data class Config(
		val filterMarkdown: Boolean,
		val showRefBeforeAndAfter: Boolean,
		val tokenizingMode: DiffTokenizingMode = DiffTokenizingMode.NONE,
		val enabled: Boolean
	)

	fun interface RawNormalizer {
		fun normalize(text: String): String
	}

	enum class Normalization {
		INITIALIZE, STOP, AS_IS
	}
}