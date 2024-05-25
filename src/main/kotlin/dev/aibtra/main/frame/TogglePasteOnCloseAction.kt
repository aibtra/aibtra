/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider

class TogglePasteOnCloseAction(
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<GuiConfiguration>("togglePasteOnClose", "Paste on Close", null, null, null, accelerators,
		configurationProvider,
		GuiConfiguration,
		{ config -> config.pasteOnClose },
		{ config: GuiConfiguration, value: Boolean -> config.copy(pasteOnClose = value) },
		{ _: GuiConfiguration -> }
	) {
	init {
		configurationProvider.listenTo(GuiConfiguration::class) {
			if (!configurationProvider.get(GuiConfiguration).hotkeyEnabled) {
				isEnabled = false
				setSelected(false)
			}
			else {
				isEnabled = true
			}
		}
	}
}