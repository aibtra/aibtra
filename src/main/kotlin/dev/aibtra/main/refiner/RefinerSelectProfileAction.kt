/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.refiner

import dev.aibtra.main.content.*
import javax.swing.*

class RefinerSelectProfileAction(
	comboBox: JComboBox<*>,
	environment: Environment
) : MainMenuAction("selectProfile", "Select Profile", "ctrl P", environment.accelerators, {
	comboBox.requestFocus()
	comboBox.showPopup()
})