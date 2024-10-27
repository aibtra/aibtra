/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.openai.OpenAIConfiguration
import javax.swing.JComboBox

class SelectProfileAction(
	comboBox: JComboBox<OpenAIConfiguration.Profile.Name>,
	environment: Environment
) : MainMenuAction("selectProfile", "Select Profile", "ctrl P", environment.accelerators, {
	comboBox.requestFocus()
	comboBox.showPopup()
})