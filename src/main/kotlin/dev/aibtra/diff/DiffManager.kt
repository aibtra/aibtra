/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.core.DebugLog
import dev.aibtra.core.Logger
import dev.aibtra.gui.Callback
import dev.aibtra.gui.Run
import dev.aibtra.gui.SequentialRunner
import dev.aibtra.gui.Ui
import dev.aibtra.text.FilteredText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import java.util.function.Consumer

class DiffManager(
	initialConfig: Config,
	private val rawNormalizer: RawNormalizer,
	coroutineDispatcher: CoroutineDispatcher,
	private val debugLog: DebugLog
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(coroutineDispatcher)
	private val stateListeners = ArrayList<(State) -> Unit>()
	private val scrollListeners = ArrayList<(raw: ScrollPos, ref: ScrollPos) -> Unit>()

	private var data: Data = Data(Input("", "", ScrollPos(0, 0), "", ScrollPos.INITIAL, initialConfig, true, null), State("", 0, listOf(), FilteredText.asIs(""), "", "", listOf(), listOf()), 0, ScrollPos.INITIAL, ScrollPos.INITIAL)
	private var inScrollPosUpdate = false
	val state: State
		get() = data.state

	fun updateRawText(raw: String, initial: Boolean = false, callback: Runnable? = null): String {
		Ui.assertEdt()

		data.let {
			if (it.input.raw == raw && !initial && callback == null) {
				return raw
			}

			// For the "initial" call, we are backing up raw to rawOrg,
			// for all subsequent calls we are reusing this backup.
			val rawOrg: String? = if (initial) {
				raw
			}
			else {
				it.input.rawOrg
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

			updateState(it.input.copy(raw = rawNew, rawOrg = rawOrgNew, callback = callback), true, "updateRaw")
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

	fun updateRawScrollPos(rawScrollPos: ScrollPos) {
		Ui.assertEdt()

		runUpdateScrollPos(Consumer {
			if (it.rawScrollPos == rawScrollPos) {
				return@Consumer
			}

			val refScrollPos = mapScrollPos(rawScrollPos,
				{ s -> s.raw },
				{ s -> s.ref },
				{ b -> b.rawFrom },
				{ b -> b.refFrom },
				{ b -> b.rawTo },
				{ b -> b.refTo })
			updateScrollPos(rawScrollPos, refScrollPos)
		})
	}

	fun updateRefScrollPos(refScrollPos: ScrollPos) {
		Ui.assertEdt()

		runUpdateScrollPos(Consumer {
			if (it.refScrollPos == refScrollPos) {
				return@Consumer
			}

			val rawScrollPos = mapScrollPos(refScrollPos,
				{ s -> s.ref },
				{ s -> s.raw },
				{ b -> b.refFrom },
				{ b -> b.rawFrom },
				{ b -> b.refTo },
				{ b -> b.rawTo })
			updateScrollPos(rawScrollPos, refScrollPos)
		})
	}

	fun updateInitial(): String? {
		Ui.assertEdt()

		data.let {
			val input = it.input
			val rawOrg = input.rawOrg ?: return null
			val normalized = rawNormalizer.normalize(rawOrg)
			updateState(input.copy(raw = normalized, rawOrg = rawOrg), true, "updateInitial")
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

	fun addStateListener(listener: (State) -> Unit) {
		Ui.assertEdt()

		stateListeners.add(listener)
	}

	fun removeStateListener(listener: (State) -> Unit) {
		Ui.assertEdt()

		stateListeners.remove(listener)
	}

	fun addScrollListener(listener: (raw: ScrollPos, ref: ScrollPos) -> Unit) {
		Ui.assertEdt()

		scrollListeners.add(listener)
	}

	private fun updateState(input: Input, forceUpdate: Boolean, debugOperationName: String?) {
		LOG.debug("updateState (schedule): operationName=" + (debugOperationName ?: "<null>") + ", raw=" + input.raw.length + ", rawOrg=" + (input.rawOrg?.length ?: "<null>") + ", ref=" + input.ref.length + ", finished=" + input.finished + ", callback=" + input.callback + ", config=" + input.config)

		val lastState = data.state
		data = Data(input, lastState, data.sequenceId + 1, data.rawScrollPos, data.refScrollPos)
		if (debugOperationName != null) {
			writeDebugFile(data.sequenceId, debugOperationName, "input-raw", data.input.raw, null)
			writeDebugFile(data.sequenceId, debugOperationName, "input-clean", data.state.filtered.clean, null)
			writeDebugFile(data.sequenceId, debugOperationName, "input-ref", data.input.ref, null)
		}

		sequentialRunner.schedule(object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				LOG.debug("updateState (run): operationName=" + (debugOperationName ?: "<null>") + ", raw=" + input.raw.length + ", rawOrg=" + (input.rawOrg?.length ?: "<null>") + ", ref=" + input.ref.length + ", finished=" + input.finished + ", callback=" + input.callback + ", config=" + input.config)

				val raw = input.raw
				val ref = input.ref
				val config = input.config
				val finished = input.finished
				val rawTo = if (finished) raw.length else computeRawTo(raw, ref, config.endBalancerCharCount, lastState)
				val blocks = if (ref.isNotEmpty()) DiffBuilder(raw.substring(0, rawTo), ref, true, true, true).build() else listOf()

				val (rawFormatted, rawChars) = DiffFormatter(DiffFormatter.Mode.KEEP_RAW_FOR_MODIFIED).format(raw, rawTo, ref, blocks)
				require(rawFormatted.length == raw.length)

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
				val (refFormatted, refChars) = DiffFormatter(mode).format(raw, rawTo, ref, blocks)
				val state = State(raw, rawTo, rawChars, filtered, ref, refFormatted, refChars, blocks)
				callback {
					Ui.assertEdt()

					val data = Data(input.copy(callback = null), state, this@DiffManager.data.sequenceId + 1, data.rawScrollPos, data.refScrollPos)
					this@DiffManager.data = data
					input.callback?.run()

					if (debugOperationName != null) {
						writeDebugFile(data.sequenceId, debugOperationName, "state-raw", data.state.raw, data.state.rawChars)
						writeDebugFile(data.sequenceId, debugOperationName, "state-clean", data.state.filtered.clean, null)
						writeDebugFile(data.sequenceId, debugOperationName, "state-ref", data.state.refFormatted, data.state.refChars)
					}

					stateListeners.toList().forEach { it(state) }
				}
			}
		}, forceUpdate)
	}

	private fun runUpdateScrollPos(consumer: Consumer<Data>) {
		require(!inScrollPosUpdate)

		inScrollPosUpdate = true
		try {
			data.let {
				consumer.accept(it)
			}
		} finally {
			inScrollPosUpdate = false
		}
	}

	private fun updateScrollPos(rawScrollPos: ScrollPos, refScrollPos: ScrollPos) {
		if (rawScrollPos == data.rawScrollPos && refScrollPos == data.refScrollPos) {
			return
		}

		if (rawScrollPos.bottom > data.state.raw.length ||
			refScrollPos.bottom > data.state.ref.length) {
			return
		}

		data = Data(data.input, data.state, data.sequenceId, rawScrollPos, refScrollPos)

		scrollListeners.toList().forEach { it(rawScrollPos, refScrollPos) }
	}

	private fun computeRawTo(raw: String, ref: String, endBalancerCharCount: Int, lastState: State): Int {
		if (lastState.rawTo == 0 // if not yet initialized, our best guess it to use equal length
			|| raw != lastState.raw // if raw has changed, everything could have changed, hence reset
			|| ref.isEmpty()  // if ref is empty or shrank, this is a new request, hence reset
			|| ref.length < lastState.ref.length
		) {
			return Math.min(raw.length, ref.length)
		}

		var rawTo = lastState.rawTo
		var refTo = lastState.ref.length
		var rawCount = 0
		var refCount = 0
		var allCount = 0
		for (block in lastState.blocks.reversed()) {
			val equal = rawTo - block.rawTo
			val refEqual = refTo - block.refTo
			require(equal == refEqual)

			rawCount += equal
			refCount += equal
			allCount += equal

			val rawChange = block.rawTo - block.rawFrom
			val refChange = block.refTo - block.refFrom
			rawCount += rawChange
			refCount += refChange
			allCount += rawChange + refChange

			rawTo = block.rawFrom - 1
			refTo = block.refFrom - 1

			if (allCount > endBalancerCharCount) {
				break
			}
		}

		val imbalance = rawCount - refCount
		val balancedRawTo = lastState.rawTo - imbalance
		return Math.min(raw.length, Math.max(balancedRawTo, lastState.rawTo))
	}

	private fun writeDebugFile(sequenceId: Int, operationName: String, type: String, text: String, diffChars: List<DiffChar>?) {
		debugLog.run("diffManager-${sequenceId}-${operationName}-${type}") { log, active ->
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

	private fun mapScrollPos(
		pos: ScrollPos,
		srcText: (state: State) -> String, dstText: (state: State) -> String,
		srcFrom: (block: DiffBlock) -> Int, dstFrom: (block: DiffBlock) -> Int,
		srcTo: (block: DiffBlock) -> Int, dstTo: (block: DiffBlock) -> Int
	): ScrollPos {
		return ScrollPos(
			mapScrollPos(pos.top, srcText, dstText, srcFrom, dstFrom, srcTo, dstTo),
			mapScrollPos(pos.bottom, srcText, dstText, srcFrom, dstFrom, srcTo, dstTo)
		)
	}

	@Suppress("NAME_SHADOWING")
	private fun mapScrollPos(
		srcPos: Int,
		srcText: (state: State) -> String, dstText: (state: State) -> String,
		srcFrom: (block: DiffBlock) -> Int, dstFrom: (block: DiffBlock) -> Int,
		srcTo: (block: DiffBlock) -> Int, dstTo: (block: DiffBlock) -> Int
	): Int {
		val state = data.state
		val srcMax = srcText(state).length - 1
		val dstMax = dstText(state).length - 1
		val beforeIndex = state.blocks.binarySearch {
			srcFrom(it).compareTo(srcPos)
		}.let {
			if (it < 0) -it - 2 else it
		}

		if (beforeIndex < 0) {
			return srcPos
		}

		if (beforeIndex >= state.blocks.size) {
			return dstMax - Math.max(0, Math.min(dstMax - (srcMax - srcPos), dstMax))
		}

		val lowerBlock = state.blocks[beforeIndex]
		val srcTo = srcTo(lowerBlock)
		val dstTo = dstTo(lowerBlock)
		if (srcPos >= srcTo) {
			return Math.min(dstTo + (srcPos - srcTo), dstMax)
		}

		val srcFrom = srcFrom(lowerBlock)
		val dstFrom = dstFrom(lowerBlock)
		require(srcPos >= srcFrom)

		val ratio = (srcPos - srcFrom) / (srcTo - srcFrom).toDouble()
		return dstFrom + ((dstTo - dstFrom) * ratio).toInt()
	}

	class State(val raw: String, val rawTo: Int, val rawChars: List<DiffChar>, val filtered: FilteredText, val ref: String, val refFormatted: String, val refChars: List<DiffChar>, val blocks: List<DiffBlock>)

	private data class Input(val raw: String, val rawOrg: String?, val rawScrollPos: ScrollPos, val ref: String, val refScrollPos: ScrollPos, val config: Config, val finished: Boolean, val callback: Runnable?)

	private class Data(val input: Input, val state: State, val sequenceId: Int, val rawScrollPos: ScrollPos, val refScrollPos: ScrollPos)

	companion object {
		private val LOG = Logger.getLogger(this::class)

		fun getSelectedBlocksFromRef(state: State, range: IntRange): List<DiffBlock> {
			require(range.first >= 0 && range.last < state.refFormatted.length)

			val refFrom = state.refChars[range.first].posRef
			val refTo = state.refChars[range.last].posRef
			require(refFrom >= 0 && refTo < state.ref.length)

			val selected = ArrayList<DiffBlock>()
			for (block in state.blocks) {
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
		val filterMarkdown: Boolean = true,
		val showRefBeforeAndAfter: Boolean = true,
		val endBalancerCharCount: Int = 50,
		val debugLogDirectory: String? = null
	) {
		companion object : ConfigurationFactory<Config> {
			override fun name(): String = "diff"

			override fun default(): Config {
				return Config()
			}
		}
	}

	fun interface RawNormalizer {
		fun normalize(text: String): String
	}

	data class ScrollPos(val top: Int, val bottom: Int) {
		companion object {
			val INITIAL = ScrollPos(0, 0)
		}
	}
}