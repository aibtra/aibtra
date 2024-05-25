/*
 * Copyright 2024 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider

class ToggleSubmitOnInvocationAction(
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<GuiConfiguration>("toggleSubmitOnInvocation", "Submit on Invocation", null, null, null, accelerators,
		configurationProvider,
		GuiConfiguration,
		{ config -> config.submitOnInvocation },
		{ config: GuiConfiguration, value: Boolean -> config.copy(submitOnInvocation = value) },
		{ config: GuiConfiguration -> }
	)