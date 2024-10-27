package dev.aibtra.main.frame

import dev.aibtra.core.Logger
import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WorkFile(val mainScope: CoroutineScope, val dialogDisplayer: DialogDisplayer) {

	private val stateListeners = ArrayList<(State?) -> Unit>()

	var state: State? = null
		private set

	fun load(path: Path) {
		Ui.assertEdt()

		mainScope.launch(Dispatchers.IO) {
			val (contentRaw, lastModifiedTime) = try {
				Pair(path.readText(), path.getLastModifiedTime().toMillis())
			} catch (e: IOException) {
				Dialogs.showError("Load", "Failed to open file: ${e.message}", dialogDisplayer)
				return@launch
			}

			val eol = determineLineEnding(contentRaw)
			val content = if (eol != null && eol != Eol.UNIX) {
				contentRaw.replace(eol.sequence, "\n")
			}
			else {
				contentRaw
			}

			Ui.runInEdt {
				if (eol == null) {
					Dialogs.showError("Load", "Files with mixed line endings can't be processed!", dialogDisplayer)
					return@runInEdt
				}

				updateState(State(content, path, eol, lastModifiedTime, content, true))
			}
		}
	}

	fun setContent(content: String) {
		Ui.assertEdt()

		state?.let { state ->
			if (state.content == content) {
				return@let
			}

			updateState(state.copy(content = content, initial = false))
		}
	}

	fun save(successCallback: () -> Unit) {
		Ui.assertEdt()

		state?.let { state ->
			mainScope.launch(Dispatchers.IO) {
				val path = state.path
				val content = state.content
				val eol = state.eol

				val state = try {
					val lastModifiedTimeBefore = path.getLastModifiedTime().toMillis()
					if (lastModifiedTimeBefore != state.lastModified) {
						Ui.runInEdt {
							Dialogs.showError("Save", "File was modified on disk!", dialogDisplayer)
						}
						return@launch
					}

					val contentRaw = if (eol != Eol.UNIX) {
						content.replace("\n", eol.sequence)
					}
					else {
						content
					}

					path.writeText(contentRaw)

					val lastModifiedTime = path.getLastModifiedTime().toMillis()
					state.copy(lastModified = lastModifiedTime, orgContent = content)
				} catch (e: IOException) {
					Dialogs.showError("Save", "Failed to save file: ${e.message}", dialogDisplayer)
					return@launch
				}

				Ui.runInEdt {
					updateState(state)
					successCallback()
				}
			}
		}
	}

	fun checkSave(runnable: Runnable) {
		if (state?.modified == true) {
			Dialogs.showYesNoCancelDialog("Save Changes", "The file is modified, save changes?", "Save", "Discard", dialogDisplayer) { save ->
				if (save) {
					save {
						runnable.run()
					}
				}
				else {
					runnable.run()
				}
			}
		}
		else {
			runnable.run()
		}
	}

	fun addStateListener(listener: (State?) -> Unit) {
		Ui.assertEdt()

		stateListeners.add(listener)
	}

	private fun updateState(state: State) {
		Ui.assertEdt()

		if (state == this.state) {
			return
		}

		LOG.info("Updating working file to ${state.path} (eol=${state.eol})")

		this.state = state
		stateListeners.toList().forEach { it(state) }
	}

	enum class Eol(val sequence: String) {
		UNIX("\n"), WINDOWS("\r\n"), MACOS("\r")
	}

	data class State(val content: String, val path: Path, val eol: Eol, val lastModified: Long, val orgContent: String, val initial: Boolean) {
		val modified: Boolean = content != orgContent
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)

		@TestOnly
		internal fun determineLineEnding(text: String): Eol? {
			var lastCh: Char? = null
			var targetEol: Eol? = null
			for ((index, ch) in text.withIndex()) {
				val eol: Eol?
				if (lastCh == '\r' && ch == '\n') {
					eol = Eol.WINDOWS
				}
				else if (ch == '\r') {
					if (index < text.length - 1 && text[index + 1] == '\n') {
						lastCh = ch
						continue
					}
					eol = Eol.MACOS
				}
				else if (ch == '\n') {
					eol = Eol.UNIX
				}
				else {
					eol = null
				}

				if (eol != null && eol != targetEol) {
					if (targetEol == null) {
						targetEol = eol
					}
					else {
						return null
					}
				}

				lastCh = ch
			}

			return targetEol ?: Eol.UNIX // If there is no EOL at all, default to UNIX
		}
	}
}