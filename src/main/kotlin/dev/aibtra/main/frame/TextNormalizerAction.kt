/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.text.TextNormalizer

class TextNormalizerAction private constructor(
	id: String,
	title: String,
	configurationProvider: ConfigurationProvider,
	accelerators: Accelerators,
	get: (TextNormalizer.Config) -> Boolean,
	set: (TextNormalizer.Config, Boolean) -> TextNormalizer.Config
) :
	MainMenuConfigurationBooleanAction<TextNormalizer.Config>(id, title, null, null, null, accelerators,
		configurationProvider,
		TextNormalizer.Config,
		{ config -> get(config) },
		{ config: TextNormalizer.Config, value: Boolean -> set(config, value) },
		{ _: TextNormalizer.Config -> }
	) {
	companion object {
		fun createJoinLines(configurationProvider: ConfigurationProvider, accelerators: Accelerators): TextNormalizerAction {
			return TextNormalizerAction("textNormalizerJoinLines", "Join Lines", configurationProvider, accelerators,
				{ config -> config.joinLines },
				{ config: TextNormalizer.Config, value: Boolean -> config.copy(joinLines = value) }
			)
		}

		fun createFixMissingEmptyLineAfterBlockQuote(configurationProvider: ConfigurationProvider, accelerators: Accelerators): TextNormalizerAction {
			return TextNormalizerAction("fixMissingEmptyLineAfterBlockQuote", "Fix missing empty lines after block quote", configurationProvider, accelerators,
				{ config -> config.fixMissingEmptyLineAfterBlockQuote },
				{ config: TextNormalizer.Config, value: Boolean -> config.copy(fixMissingEmptyLineAfterBlockQuote = value) }
			)
		}

		fun createChangeDoubleToSingleBlockQuotes(configurationProvider: ConfigurationProvider, accelerators: Accelerators): TextNormalizerAction {
			return TextNormalizerAction("changeDoubleToSingleBlockQuotes", "Change double to single block quotes", configurationProvider, accelerators,
				{ config -> config.changeDoubleToSingleBlockQuotes },
				{ config: TextNormalizer.Config, value: Boolean -> config.copy(changeDoubleToSingleBlockQuotes = value) }
			)
		}

		fun createRewrapBlockQuotes(configurationProvider: ConfigurationProvider, accelerators: Accelerators): TextNormalizerAction {
			return TextNormalizerAction("rewrapBlockQuotes", "Rewrap block quotes", configurationProvider, accelerators,
				{ config -> config.rewrapBlockQuotes },
				{ config: TextNormalizer.Config, value: Boolean -> config.copy(rewrapBlockQuotes = value) }
			)
		}
	}
}