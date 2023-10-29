/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.gui.Callback
import dev.aibtra.gui.Run
import dev.aibtra.gui.SequentialRunner
import dev.aibtra.gui.Ui
import dev.aibtra.text.FilteredText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import java.lang.StringBuilder
import java.nio.file.Files
import java.nio.file.Path

class DiffManager(
	initialConfig: Config,
	coroutineDispatcher: CoroutineDispatcher
) {
	private val sequentialRunner = SequentialRunner(coroutineDispatcher)
	private val listeners = ArrayList<(State) -> Unit>()
	private val debugDirectory = initializeDebugDirectory(initialConfig)
	private val debugStartTime = System.currentTimeMillis()

	private var data: Data = Data(Input("", "", initialConfig, true, null), State("", 0, listOf(), FilteredText.asIs(""), "", "", listOf(), listOf()), 0)
	val state: State
		get() = data.state

	fun updateRaw(raw: String, callback: Runnable? = null) {
		Ui.assertEdt()

		data.let {
			if (it.input.raw == raw && callback == null) {
				return
			}

			updateState(it.input.copy(raw = raw, callback = callback), true, "updateRaw")
		}
	}

	fun updateRefined(ref: String, finished: Boolean) {
		Ui.assertEdt()

		data.let {
			if (it.input.ref == ref && it.input.finished == finished) {
				return
			}

			updateState(it.input.copy(ref = ref, finished = finished), finished, if (finished) "updateRefined" else null)
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

	fun addListener(listener: (State) -> Unit) {
		listeners.add(listener)
	}

	private fun updateState(input: Input, forceUpdate: Boolean, debugOperationName: String?) {
		val lastState = data.state
		data = Data(input, lastState, data.sequenceId + 1)

		sequentialRunner.schedule(object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
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

					val data = Data(input.copy(callback = null), state, this@DiffManager.data.sequenceId + 1)
					this@DiffManager.data = data
					input.callback?.run()

					writeDebugFiles(data, debugOperationName)

					listeners.forEach { it(state) }
				}
			}
		}, forceUpdate)
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

	private fun writeDebugFiles(data: Data, operationName: String?) {
		if (debugDirectory == null || operationName == null) {
			return
		}

		writeDebugFile(data.sequenceId, operationName, "raw", data.state.raw, data.state.rawChars, debugDirectory)
		writeDebugFile(data.sequenceId, operationName, "ref", data.state.ref, data.state.refChars, debugDirectory)
	}

	private fun writeDebugFile(sequenceId: Int, operationName: String, type: String, text: String, diffChars: List<DiffChar>, debugDirectory: Path) {
		val debugFile = debugDirectory.resolve("$debugStartTime-$sequenceId-$operationName-$type.txt")
		Files.newBufferedWriter(debugFile).use { writer ->
			var textBuilder = StringBuilder()
			var diffBuilder = StringBuilder()
			require(text.length == diffChars.size)
			for (i in text.indices) {
				val ch = text[i]
				textBuilder.append(ch)
				diffBuilder.append(diffChars[i].kind.char)
				if (ch == '\n') {
					writer.write(textBuilder.toString())
					diffBuilder.append("\n")
					writer.write(diffBuilder.toString())

					textBuilder = StringBuilder()
					diffBuilder = StringBuilder()
				}
			}

			textBuilder.append("\n")
			writer.write(textBuilder.toString())
			diffBuilder.append("\n")
			writer.write(diffBuilder.toString())
		}
	}

	class State(val raw: String, val rawTo: Int, val rawChars: List<DiffChar>, val filtered: FilteredText, val ref: String, val refFormatted: String, val refChars: List<DiffChar>, val blocks: List<DiffBlock>)

	private data class Input(val raw: String, val ref: String, val config: Config, val finished: Boolean, val callback: Runnable?)

	private class Data(val input: Input, val state: State, val sequenceId: Int)

	companion object {
		fun getSelectedBlocksFromRefined(state: State, range: IntRange): List<DiffBlock> {
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

		fun initializeDebugDirectory(config: Config): Path? {
			if (config.debugLogDirectory == null) {
				return null
			}

			val dir = Path.of(config.debugLogDirectory)
			Files.createDirectories(dir)
			return dir
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
}