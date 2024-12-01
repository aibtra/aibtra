package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.*
import java.io.*
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import kotlin.io.path.*

class RefinerWorkFile(val mainScope: CoroutineScope, val dialogDisplayer: DialogDisplayer) {

	private val watchService: WatchService
	private val stateListeners = ArrayList<(State?) -> Unit>()

	init {
		watchService = FileSystems.getDefault().newWatchService()

		mainScope.launch(Dispatchers.IO) {
			watchService.use { service ->
				while (isActive) {
					val watchKey: WatchKey = try {
						val watchKey = service.take()
						watchKey.also {
							for (event in it.pollEvents()) {
								val dir = it.watchable() as Path
								val name = event.context() as Path
								val path = dir.resolve(name)
								val lastModifiedTime = path.getLastModifiedTime().toMillis()
								val fileSize = path.fileSize()

								Ui.runInEdt {
									markModifiedExternally(lastModifiedTime, fileSize)
								}
							}
						}
					} catch (e: IOException) {
						LOG.error(e)
						continue
					} catch (e: ClosedWatchServiceException) {
						return@use
					}

					val valid = watchKey.reset()
					if (!valid) {
						LOG.info("Key is no longer valid. Stopping monitoring.")
						break
					}
				}
			}
		}
	}

	var state: State? = null
		private set

	fun load(path: Path, line: Int?) {
		Ui.assertEdt()

		loadContent(path, line)
	}

	fun setContent(content: String) {
		Ui.assertEdt()

		state?.let { state ->
			if (state.content == content) {
				return@let
			}

			updateState(state.copy(content = content, initial = false, initialLine = null))
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

	fun checkSave(preDialogRunnable: Runnable, runnable: Runnable) {
		if (state?.modified == true) {
			preDialogRunnable.run()

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

	fun checkModifiedExternally() {
		Ui.assertEdt()

		state?.let {
			if (!it.modifiedExternally) {
				return
			}

			if (it.failure) {
				return
			}

			if (!it.modified) {
				loadContent(it.path, null)
				return
			}

			updateState(it.copy(modifiedExternally = false))

			Dialogs.showConfirmationDialog("Load", "File has been modified on disk and in memory. Do you want to discard local changes and reload?", "Discard & Reload", dialogDisplayer) {
				load(it.path, null)
				return@showConfirmationDialog
			}
		}
	}

	fun addStateListener(listener: (State?) -> Unit) {
		Ui.assertEdt()

		stateListeners.add(listener)
	}

	fun dispose() {
		try {
			watchService.close()
		} catch (ex: IOException) {
			LOG.error(ex)
		}
	}

	private fun markModifiedExternally(lastModifiedTime: Long, fileSize: Long) {
		Ui.assertEdt()

		state?.let {
			if (it.lastModified == lastModifiedTime &&
				it.size == fileSize) {
				return
			}

			updateState(it.copy(modifiedExternally = true))
		}
	}

	private fun loadContent(path: Path, line: Int?) {
		Ui.assertEdt()

		state?.watchKey?.let {
			mainScope.launch(Dispatchers.IO) {
				it.reset()
			}
		}

		mainScope.launch(Dispatchers.IO) {
			val (contentRaw, lastModifiedTime, size) = try {
				Triple(path.readText(), path.getLastModifiedTime().toMillis(), path.fileSize())
			} catch (e: IOException) {
				Ui.runInEdt {
					state?.let {
						updateState(it.copy(failure = true))
					}

					Dialogs.showError("Load", "Failed to open file: ${e.message}", dialogDisplayer)
				}
				return@launch
			}

			val watchKey: WatchKey? = try {
				path.parent.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
			} catch (e: Exception) {
				LOG.error(e)
				null
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

				updateState(State(content, path, watchKey, eol, lastModifiedTime, size, content, false, true, line, false))
			}
		}
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

	data class State(val content: String, val path: Path, val watchKey: WatchKey?, val eol: Eol, val lastModified: Long, val size: Long, val orgContent: String, val modifiedExternally: Boolean, val initial: Boolean, val initialLine: Int?, val failure: Boolean) {
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