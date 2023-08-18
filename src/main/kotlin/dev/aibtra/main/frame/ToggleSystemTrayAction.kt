/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider

class ToggleSystemTrayAction(
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<GuiConfiguration>("toggleSystemTray", "Close to Tray", null, null, null, accelerators,
		configurationProvider,
		GuiConfiguration,
		{ config -> config.systemTray },
		{ config: GuiConfiguration, value: Boolean -> config.copy(systemTray = value) },
		{ }
	)
