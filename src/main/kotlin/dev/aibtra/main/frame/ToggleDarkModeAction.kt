/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider

class ToggleDarkModeAction(
	theme: Theme,
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<GuiConfiguration>("toggleDarkMode", "Dark Mode", Icons.DARK_MODE, "Dark Mode", null, accelerators,
		configurationProvider,
		GuiConfiguration,
		{ config -> config.darkTheme },
		{ config: GuiConfiguration, value: Boolean -> config.copy(darkTheme = value) },
		{ _: GuiConfiguration -> theme.switch() }
	)
