package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.core.WorkingMode
import dev.aibtra.openai.OpenAIConfiguration

class ProfileManager(initialWorkingMode: WorkingMode, val configurationProvider: ConfigurationProvider) {

	private val listeners = ArrayList<(OpenAIConfiguration.Profile.Name, OpenAIConfiguration.Profile.Name) -> Unit>()

	var workingMode: WorkingMode = initialWorkingMode
		set(workingMode) {
			// If the frame has workingMode != open set, we won't override this by open
			if (workingMode != WorkingMode.open && field != workingMode) {
				val currentProfile = profile()
				configurationProvider.change(OpenAIConfiguration) {
					it.copy(workingModeToDefaultProfileId = it.workingModeToDefaultProfileId.plus(field to currentProfile.name.id))
				}

				field = workingMode
			}
		}

	private var name: OpenAIConfiguration.Profile.Name = configurationProvider.get(OpenAIConfiguration).currentProfile(workingMode).name

	fun profile(): OpenAIConfiguration.Profile {
		val configuration = configurationProvider.get(OpenAIConfiguration)
		return configuration.profile(name.id) ?: configuration.currentProfile(workingMode)
	}

	fun setProfile(name: OpenAIConfiguration.Profile.Name) {
		if (name.id == this.name.id) {
			return
		}

		val lastName = this.name
		this.name = name

		configurationProvider.change(OpenAIConfiguration) {
			it.copy(workingModeToDefaultProfileId = it.workingModeToDefaultProfileId.plus(workingMode to name.id))
		}

		fireChanged(lastName, name)
	}

	fun overrideProfile(id: String) {
		val name = configurationProvider.get(OpenAIConfiguration).profile(id)?.name
		if (name == null || name.id == this.name.id) {
			return
		}

		this.name = name

		fireChanged(name, name)
	}

	fun updateCurrentProfile(update: (OpenAIConfiguration.Profile) -> OpenAIConfiguration.Profile): OpenAIConfiguration.Profile {
		configurationProvider.change(OpenAIConfiguration) {
			OpenAIConfiguration.replaceProfile(it, it.currentProfile(workingMode)) { profile -> update(profile) }
		}
		fireChanged(name, name)
		return configurationProvider.get(OpenAIConfiguration).currentProfile(workingMode)
	}

	fun addListener(listener: (OpenAIConfiguration.Profile.Name, OpenAIConfiguration.Profile.Name) -> Unit) {
		listeners.add(listener)
	}

	fun fireInitialization() {
		fireChanged(name, name)
	}

	private fun fireChanged(lastName: OpenAIConfiguration.Profile.Name, name: OpenAIConfiguration.Profile.Name) {
		listeners.forEach { it(lastName, name) }
	}
}