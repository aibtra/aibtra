/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import dev.aibtra.gui.Ui
import dev.aibtra.gui.action.DefaultAction
import net.miginfocom.swing.MigLayout
import javax.swing.JDialog

class OkCancelDialog(
	private val title: String,
	val content: () -> Content
) {
	fun show(dialogDisplayer: DialogDisplayer) {
		Ui.assertEdt()

		dialogDisplayer.show { frame ->
			val dialog = JDialog(frame, title)
			val contentPane = dialog.contentPane
			val layout = MigLayout(
				"",
				"[grow][][]",
				"[][]"
			)
			contentPane.layout = layout

			val content = content()
			val okAction = DefaultAction("OK") {
				try {
					content.apply(DialogDisplayer.create(dialog))
				} catch (e: InputFailedException) {
					Dialogs.showWarning(title, requireNotNull(e.message), dialogDisplayer)
				}
				dialog.dispose()
			}

			val cancelAction = DefaultAction("Cancel") {
				dialog.dispose()
			}

			val okButton = Ui.createButton(okAction)
			contentPane.add(content.panel.control(), "cell 0 0, span")
			contentPane.add(okButton, "cell 2 2")
			contentPane.add(Ui.createButton(cancelAction), "cell 4 2")

			dialog.rootPane.defaultButton = okButton

			dialog.pack()
			dialog.setLocationRelativeTo(frame)
			dialog.isVisible = true
		}
	}

	class Content(val panel: Panel, val apply: (DialogDisplayer) -> Unit)

	class InputFailedException(message: String) : Exception(message)
}