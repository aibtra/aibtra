/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.gui.dialogs

import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JTextField

class Editor private constructor(
	title: String,
	private val textField: JTextField
) {
	val label: JLabel
	val text: String
		get() = textField.text

	init {
		label = JLabel(title)
		label.text = title
		label.labelFor = textField
	}

	fun getLabel(): Component = label

	fun getInput(): Component = textField

	companion object {
		fun password(title: String): Editor {
			return Editor(title, JPasswordField(30))
		}
	}
}