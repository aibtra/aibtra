package dev.aibtra.main.frame

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.openai.*

class ProfileManager(private val workingMode: WorkingMode, val configurationProvider: ConfigurationProvider) {

	private val listeners = ArrayList<(OpenAIRefinementConfiguration.Profile.Name, OpenAIRefinementConfiguration.Profile.Name) -> Unit>()
	private var name: OpenAIRefinementConfiguration.Profile.Name = configurationProvider.get(OpenAIRefinementConfiguration).currentProfile(workingMode).name

	fun profile(): OpenAIRefinementConfiguration.Profile {
		val configuration = configurationProvider.get(OpenAIRefinementConfiguration)
		return configuration.profile(name.id) ?: configuration.currentProfile(workingMode)
	}

	fun profiles(): List<OpenAIRefinementConfiguration.Profile?> {
		val configuration = configurationProvider.get(OpenAIRefinementConfiguration)
		return configuration.profiles
	}

	fun getProfile(name: OpenAIRefinementConfiguration.Profile.Name): OpenAIRefinementConfiguration.Profile? {
		val configuration = configurationProvider.get(OpenAIRefinementConfiguration)
		return configuration.profile(name.id)
	}

	fun setProfile(name: OpenAIRefinementConfiguration.Profile.Name) {
		if (name.id == this.name.id) {
			return
		}

		val lastName = this.name
		this.name = name

		configurationProvider.change(OpenAIRefinementConfiguration) {
			it.copy(workingModeToDefaultProfileId = it.workingModeToDefaultProfileId.plus(workingMode to name.id))
		}

		fireChanged(lastName, name)
	}

	fun overrideProfile(id: String) {
		val name = configurationProvider.get(OpenAIRefinementConfiguration).profile(id)?.name
		if (name == null || name.id == this.name.id) {
			return
		}

		this.name = name

		fireChanged(name, name)
	}

	fun updateCurrentProfile(update: (OpenAIRefinementConfiguration.Profile) -> OpenAIRefinementConfiguration.Profile): OpenAIRefinementConfiguration.Profile {
		configurationProvider.change(OpenAIRefinementConfiguration) {
			OpenAIRefinementConfiguration.replaceProfile(it, it.currentProfile(workingMode)) { profile -> update(profile) }
		}
		fireChanged(name, name)
		return configurationProvider.get(OpenAIRefinementConfiguration).currentProfile(workingMode)
	}

	fun addListener(listener: (OpenAIRefinementConfiguration.Profile.Name, OpenAIRefinementConfiguration.Profile.Name) -> Unit) {
		listeners.add(listener)
	}

	fun fireInitialization() {
		fireChanged(name, name)
	}

	private fun fireChanged(lastName: OpenAIRefinementConfiguration.Profile.Name, name: OpenAIRefinementConfiguration.Profile.Name) {
		listeners.forEach { it(lastName, name) }
	}
}