/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider

class ToggleSubmitOnProfileChangeAction(
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<GuiConfiguration>("toggleSubmitOnProfileChange",
		"Submit on Profile Change",
		null,
		null,
		null,
		accelerators,
		configurationProvider,
		GuiConfiguration,
		{ config -> config.submitOnProfileChange },
		{ config: GuiConfiguration, value: Boolean -> config.copy(submitOnProfileChange = value) },
		{ config: GuiConfiguration -> }
	)