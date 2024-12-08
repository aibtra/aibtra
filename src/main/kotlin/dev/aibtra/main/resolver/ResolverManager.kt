package dev.aibtra.main.resolver

import dev.aibtra.core.*
import dev.aibtra.diff.*
import dev.aibtra.gui.*
import dev.aibtra.resolver.*
import kotlinx.coroutines.*

class ResolverManager(
	coroutineDispatcher: CoroutineDispatcher,
	mainScope: CoroutineScope
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(coroutineDispatcher, mainScope)
	private val stateListeners = ArrayList<(State, State) -> Unit>()
	val summaryScrollState = ScrollState()

	private var data: Data = Data(Input.INITIAL, State.INITIAL, 0)

	val state: State
		get() = data.state

	fun updateSnippets(snippets: ResolverSnippets) {
		Ui.assertEdt()

		val texts = ResolverTexts.createFrom(snippets)
		updateState(Input(snippets, null, texts), "updateSnippets")
	}

	fun updateResolutions(resolutions: ResolverResolutions) {
		Ui.assertEdt()

		// The snippets may have changed in the meantime (e.g., by editing and saving).
		val adjusted = data.input.snippets?.let {
			resolutions.replaceSnippets(it)
		}

		updateState(data.input.copy(resolutions = adjusted), "updateResolutions")
	}

	fun updateDraftSummaryContent(summaryContent: ResolverSummaryContent) {
		Ui.assertEdt()

		data.let { d ->
			d.input.snippets?.let {
				val texts = summaryContent.split(it)
				d.input.texts?.let { other ->
					if (texts.equalsContent(other)) {
						return
					}
				}

				updateState(d.input.copy(texts = texts), "updateDraftSummaryContent")
			}
		}
	}

	fun addStateListener(listener: (state: State, last: State) -> Unit) {
		Ui.assertEdt()

		stateListeners.add(listener)
	}

	private fun updateState(input: Input, debugOperationName: String?) {
		LOG.debug("updateState (schedule): operationName=" + (debugOperationName ?: "<null>"))

		val dataState = data.state
		data = Data(input, dataState, data.sequenceId + 1)

		sequentialRunner.schedule(object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				val diffs = input.texts?.let { texts ->
					ResolverDiffs.build(texts, input.resolutions)
				}

				val summaries = diffs?.let {
					ResolverSummaries.build(it)
				}

				callback {
					Ui.assertEdt()

					val latestData = this@ResolverManager.data
					val latestInput = latestData.input
					val latestState = latestData.state
					val newState = State(latestInput.snippets, latestInput.resolutions, latestInput.texts, diffs, summaries)
					val data = Data(latestInput, newState, this@ResolverManager.data.sequenceId + 1)
					this@ResolverManager.data = data

					stateListeners.toList().forEach { it(newState, latestState) }

					newState.summaries?.let {
						summaryScrollState.updateLeftText(it.drafts.content.text)
						summaryScrollState.updateRightText(it.resolutions.content.text)
						summaryScrollState.updateDiffBlocks(it.scrollDiffBlocks)
					}
				}
			}
		}, true)
	}

	data class Input(
		val snippets: ResolverSnippets?,
		val resolutions: ResolverResolutions?,
		val texts: ResolverTexts?
	) {
		init {
			require(resolutions == null || resolutions.snippets == snippets)
			require(texts == null || texts.snippets == snippets)
		}

		companion object {
			val INITIAL: Input

			init {
				INITIAL = Input(null, null, null)
			}
		}
	}

	data class State(
		val snippets: ResolverSnippets?,
		val resolutions: ResolverResolutions?,
		val texts: ResolverTexts?,
		val diffs: ResolverDiffs?,
		val summaries: ResolverSummaries?
	) {
		init {
			require(resolutions == null || resolutions.snippets == snippets)
			require(texts == null || texts.snippets == snippets)
			require(diffs == null || diffs.snippets == snippets)
		}

		companion object {
			val INITIAL: State

			init {
				INITIAL = State(null, null, null, null, null)
			}
		}
	}

	private class Data(val input: Input, val state: State, val sequenceId: Int)

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}