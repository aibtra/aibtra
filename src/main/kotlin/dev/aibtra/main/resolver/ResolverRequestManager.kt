/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.resolver

import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.openai.*
import dev.aibtra.resolver.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.file.*
import java.util.concurrent.atomic.*

class ResolverRequestManager(
	private val resolverManager: ResolverManager,
	private val environment: Environment,
	private val dialogDisplayer: DialogDisplayer,
	private val progressTexter: (text: String?) -> Unit
) {
	private val sequentialRunner = SequentialRunner.createGuiThreadRunner(environment.coroutineDispatcher, environment.mainScope)
	private val inProgressListeners = ArrayList<InProgressListener>()
	private val currentRun: AtomicReference<Run?> = AtomicReference(null)
	var inProgress = false
		private set

	fun submit(request: Request) {
		Submitter(environment, dialogDisplayer) { apiToken, failureHandler ->
			schedule(request, apiToken, failureHandler)
		}.submit()
	}

	fun stopCurrent() {
		Ui.assertEdt()

		currentRun.set(null)
		progressTexter(null)
		inProgress = false
		notifyInProgress(false)
	}

	fun addProgressListener(listener: InProgressListener) {
		inProgressListeners.add(listener)
	}

	private fun schedule(request: Request, apiToken: String, failureHandler: RequestManagerFailureHandler) {
		val run = object : Run {
			override suspend fun invoke(callback: Callback, coroutineScope: CoroutineScope) {
				notifyInProgress(true)

				var resolutions: ResolverResolutions? = null
				try {
					resolutions = query(apiToken, request, failureHandler) {
						this == currentRun.get()
					}
				} catch (ioe: IOException) {
					Dialogs.showIOError(ioe, dialogDisplayer)
				} finally {
					if (this == currentRun.get()) {
						resolutions?.let { res ->
							callback {
								resolverManager.updateResolutions(res)
							}
						}

						progressTexter(null)
						inProgress = false
						notifyInProgress(false)
					}
				}
			}
		}
		currentRun.set(run)
		if (!inProgress) {
			inProgress = true
			notifyInProgress(inProgress)
		}
		sequentialRunner.schedule(run, true)
	}

	private fun query(apiToken: String, request: Request, failureHandler: RequestManagerFailureHandler, shallContinue: () -> Boolean): ResolverResolutions? {
		val resolverFiles = ResolverFiles.parseOverviewFile(request.overviewFile)
		val snippets = ResolverSnippets.compute(resolverFiles, CONTEXT_SIZE)
		if (snippets.size() == 0) {
			Ui.runInEdt {
				Dialogs.showWarning("Resolver", "No conflicts were identified.\n\nDo you possibly have only Deleted-By or Added-By conflicts? Such conflicts will not be processed.", dialogDisplayer)
			}

			return null
		}

		Ui.runInEdt {
			resolverManager.updateSnippets(snippets)
		}

		if (request.skipInCaseOfWarnings && snippets.any { it.warning != null }) {
			return null
		}

		val configuration = environment.configurationProvider.get(OpenAIResolverConfiguration)
		val service = OpenAIResolverService(apiToken, environment.debugLog, environment.paths)
		val profile: OpenAIResolverConfiguration.Profile = configuration.profiles[0]
		val approaches = profile.approaches
		require(approaches.size == 1)

		var result: ResolverResolutions? = null
		service.request(approaches[0], snippets, request.resolverPacket, object : OpenAIService.FailureHandler {
			override fun process(failure: IOException, mightBeAuthentication: Boolean) {
				failureHandler.process(failure, mightBeAuthentication)
			}
		}, object : OpenAIResolverService.Callback {
			override fun startSummaries() {
				progressTexter("summarizing")
			}

			override fun handleSummaries(content: String): Boolean {
				return shallContinue()
			}

			override fun startMerges() {
				progressTexter("merging")
			}

			override fun handleMerges(content: String): Boolean {
				return shallContinue()
			}

			override fun startResolutions() {
				progressTexter("resolving")
			}

			override fun handleResolutions(resolutions: ResolverResolutions) {
				result = resolutions
			}

			override fun finish() {
				progressTexter(null)
			}
		})

		return result
	}

	private fun notifyInProgress(inProgress: Boolean) {
		Ui.runInEdt {
			for (inProgressListener in inProgressListeners) {
				inProgressListener.setInProgress(inProgress)
			}
		}
	}

	fun interface InProgressListener {
		fun setInProgress(inProgress: Boolean)
	}

	class Request(val overviewFile: Path, val skipInCaseOfWarnings: Boolean, val resolverPacket: ResolverPacket?)

	companion object {
		const val CONTEXT_SIZE = 10
	}
}