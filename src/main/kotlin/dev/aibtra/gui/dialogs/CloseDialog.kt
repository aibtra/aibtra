/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import dev.aibtra.gui.*
import dev.aibtra.gui.action.*
import net.miginfocom.swing.*
import javax.swing.*

class CloseDialog(
	private val title: String,
	val content: () -> Content
) {
	fun show(dialogDisplayer: DialogDisplayer) {
		Ui.assertEdt()

		dialogDisplayer.show { frame ->
			val dialog = JDialog(frame, title)
			val contentPane = dialog.contentPane
			val layout = MigLayout(
				"fill",
				"[grow][][]",
				"[grow][]"
			)
			contentPane.layout = layout

			val content = content()
			val closeAction = DefaultAction("Close") {
				dialog.dispose()
			}

			val closeButton = Ui.createButton(closeAction)
			contentPane.add(content.panel, "cell 0 0, span, grow")
			contentPane.add(closeButton, "cell 2 2")

			dialog.rootPane.defaultButton = closeButton

			dialog.pack()
			dialog.setLocationRelativeTo(frame)
			dialog.isVisible = true
		}
	}

	class Content(val panel: JPanel)
}