package dev.aibtra.main.resolver

import dev.aibtra.core.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.resolver.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.io.path.*

class ResolverSaver(private val resolverManager: ResolverManager, val environment: Environment, val dialogDisplayer: DialogDisplayer) {

	private val listeners = mutableListOf<(ResolverSaver) -> Unit>()
	private var modified = false

	init {
		resolverManager.addStateListener { state, _ ->
			state.texts?.let { texts ->
				modified = ResolverTexts.getModified(texts).isNotEmpty()
			}

			listeners.forEach { it(this) }
		}
	}

	fun isModified(): Boolean {
		return modified
	}

	fun save(successCallback: () -> Unit) {
		environment.mainScope.launch(Dispatchers.IO) {
			val state = resolverManager.state
			state.texts?.let { texts ->
				try {
					val portionToReplacement = ResolverTexts.getModified(texts).associate {
						Pair(it.snippet.draftPortion, it.text)
					}

					val fileToReplacement = ResolverDraftPortion.applyReplacements(portionToReplacement)
					val repoRoot = texts.snippets.files.repoRoot
					for (replacement in fileToReplacement.values) {
						val draftPath = repoRoot.resolve(replacement.draftPathRel)
						if (draftPath.getLastModifiedTime() != replacement.lastModifiedTime) {
							throw IOException("File '$draftPath' has been modified on disk. Can't store changes.")
						}

						val content = replacement.draftContent
						val draft = StringUtils.applyLineEndings(content, replacement.draftEol)
						draftPath.writeText(draft)
					}

					val resolverFiles = ResolverFiles.parseOverviewFile(texts.snippets.files.overviewFile)
					val snippets = ResolverSnippets.compute(resolverFiles, 10)
					Ui.runInEdt {
						resolverManager.updateSnippets(snippets)
						state.resolutions?.let {
							resolverManager.updateResolutions(it.replaceSnippets(snippets))
						}
						modified = false
						successCallback()
					}
				} catch (e: IOException) {
					Dialogs.showError("Save", "Failed to save file: ${e.message}", dialogDisplayer)
					return@launch
				}
			}
		}
	}

	fun checkSave(run: (ResolverSnippets?) -> Unit) {
		resolverManager.state.snippets?.let { snippets ->
			resolverManager.state.texts?.let { texts ->
				if (ResolverTexts.getModified(texts).isEmpty()) {
					run(snippets)
				}
				else {
					Dialogs.showYesNoCancelDialog("Save Changes", "Some files have been modified, save changes?", "Save", "Discard", dialogDisplayer) { save ->
						if (save) {
							save {
								run(snippets)
							}
						}
						else {
							run(snippets)
						}
					}
				}
			}
		} ?: run(null)
	}

	fun addStateListener(listener: (ResolverSaver) -> Unit) {
		listeners.add(listener)
	}
}