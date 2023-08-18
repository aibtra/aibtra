/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.JPanel

class Panel(
	private val rows: Int,
	private val cols: Int
) {
	private val panel: JPanel

	init {
		panel = JPanel()
		panel.layout = MigLayout("insets 0")
	}

	fun add(component: Component, row: Int, col: Int, span: Int = 1, grow: Boolean = false) {
		require(row in 0 until rows)
		require(col in 0 until cols)

		val constraints = StringBuilder()
		constraints.append("cell $col $row")
		if (span != 1) {
			constraints.append(", span $span")
		}
		if (grow) {
			constraints.append(", growX")
		}
		panel.add(component, constraints.toString())
	}

	fun add(editor: Editor, row: Int, col: Int) {
		add(editor.label, row, col)
		add(editor.getInput(), row, col + 2, grow = true)
	}

	fun control(): Component = panel
}