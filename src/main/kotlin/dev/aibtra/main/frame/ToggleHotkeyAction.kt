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
			val hotkeyEnabled = hotkeyListener.update()
			if (hotkeyEnabled) {
				Dialogs.showInfoDialog(
					"Hotkey",
					"The hotkey has now been activated. Pressing ${HotkeyListener.ACCELERATOR_DESCRIPTION} in any application will invoke Aibtra.\n\nIf the hotkey does not work immediately, please exit and restart Aibtra.", dialogDisplayer
				)
			}
		}
	)
