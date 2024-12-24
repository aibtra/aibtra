/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.core.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import dev.aibtra.refiner.*
import java.nio.file.*
import javax.swing.*

internal class RefinerFileTab(tabbedPane: MainTabbedPane, environment: Environment, dialogDisplayer: DialogDisplayer) : RefinerTextualTab(WorkingMode.FILE, tabbedPane, environment, dialogDisplayer) {
	private val workFile: RefinerWorkFile

	init {
		workFile = RefinerWorkFile(environment.mainScope, dialogDisplayer)

		workFile.addStateListener { state ->
			if (state != null && state.initial) {
				val text = state.content
				diffManager.updateRawText(text, null, profileManager.profile().diffConfig, RefinerDiffManager.Normalization.STOP, null)
				rawEditor.setText(text)

				state.initialLine?.let {
					// Needs to be postponed to function correctly
					Ui.runInEdt {
						rawEditor.scrollToLine(it)
					}
				}
			}

			updateTitle()
		}
	}

	override fun getTitle() : String {
		return workFile.state?.let {
			val name = it.path.fileName.toString()
			name + if (it.modified) "*" else ""
		} ?: "<untitled>"
	}

	override fun updateContent() {
		super.updateContent()
		workFile.setContent(rawEditor.getText())
	}

	override fun checkClose(runnable: Runnable) {
		workFile.checkSave({
			toFront()
		}, runnable)
	}

	override fun addTextualFileActions(menu: JMenu) {
		addAction(menu, RefinerSaveAction(workFile, environment))
	}

	override fun focusGained() {
		workFile.checkModifiedExternally()
	}

	override fun dispose() {
		workFile.dispose()
	}

	fun getFile(): Path? {
		return workFile.state?.path
	}

	fun setFile(fileToOpen: Path, profileId: String?, line: Int?) {
		updateProfile(profileId)
		workFile.load(fileToOpen, line)
	}
}