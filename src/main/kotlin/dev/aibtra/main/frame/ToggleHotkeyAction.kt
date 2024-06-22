/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.gui.HotkeyListener
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs

class ToggleHotkeyAction(
	hotkeyListener: HotkeyListener,
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators,
	dialogDisplayer: DialogDisplayer
) :
	MainMenuConfigurationBooleanAction<GuiConfiguration>("toggleHotkey", "Enable Hotkey", null, null, null, accelerators,
		configurationProvider,
		GuiConfiguration,
		{ config -> config.hotkeyEnabled },
		{ config: GuiConfiguration, value: Boolean -> config.copy(hotkeyEnabled = value) },
		{ config: GuiConfiguration ->
			hotkeyListener.update()

			if (config.hotkeyEnabled) {
				Dialogs.showInfoDialog(
					"Hotkey",
					"The hotkey is now active. Pressing Ctrl-C-C in any application will invoke Aibtra.", dialogDisplayer
				)
			}
		}
	)
