/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.diff.DiffManager

class ToggleFilterMarkdownAction(
	diffManager: DiffManager,
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<DiffManager.Config>("toggleFilterMarkdown", "Filter Markdown", null, null, null, accelerators,
		configurationProvider,
		DiffManager.Config,
		{ config -> config.filterMarkdown },
		{ config: DiffManager.Config, value: Boolean -> config.copy(filterMarkdown = value) },
		{ config: DiffManager.Config -> diffManager.setConfig(config) }
	)
