/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.diff.DiffManager

class ToggleShowRefBeforeAndAfterAction(
	diffManager: DiffManager,
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators
) :
	MainMenuConfigurationBooleanAction<DiffManager.Config>("toggleShowRefBeforeAndAfter", "Show Diff Before/After", Icons.SHOW_REMOVED, "Before/After", null, accelerators,
		configurationProvider,
		DiffManager.Config,
		{ config -> config.showRefBeforeAndAfter },
		{ config: DiffManager.Config, value: Boolean -> config.copy(showRefBeforeAndAfter = value) },
		{ config: DiffManager.Config -> diffManager.setConfig(config) }
	)
