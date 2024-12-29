package dev.aibtra.main.refiner

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.ai.*

class RefinerProfileManager(private val workingMode: WorkingMode, val configurationProvider: ConfigurationProvider) {

	private val listeners = ArrayList<(AIProfile.Name, AIProfile.Name) -> Unit>()
	private var name: AIProfile.Name = configurationProvider.get(AIRefinementConfiguration).currentProfile(workingMode).name

	fun profile(): AIRefinementConfiguration.Profile {
		val configuration = configurationProvider.get(AIRefinementConfiguration)
		return configuration.profile(name.id) ?: configuration.currentProfile(workingMode)
	}

	fun profiles(): List<AIRefinementConfiguration.Profile?> {
		val configuration = configurationProvider.get(AIRefinementConfiguration)
		return configuration.profiles
	}

	fun getProfile(name: AIProfile.Name): AIRefinementConfiguration.Profile? {
		val configuration = configurationProvider.get(AIRefinementConfiguration)
		return configuration.profile(name.id)
	}

	fun setProfile(name: AIProfile.Name) {
		if (name.id == this.name.id) {
			return
		}

		val lastName = this.name
		this.name = name

		configurationProvider.change(AIRefinementConfiguration) {
			it.copy(workingModeToDefaultProfileId = it.workingModeToDefaultProfileId.plus(workingMode to name.id))
		}

		fireChanged(lastName, name)
	}

	fun overrideProfile(id: String) {
		val name = configurationProvider.get(AIRefinementConfiguration).profile(id)?.name
		if (name == null || name.id == this.name.id) {
			return
		}

		this.name = name

		fireChanged(name, name)
	}

	fun updateCurrentProfile(update: (AIRefinementConfiguration.Profile) -> AIRefinementConfiguration.Profile): AIRefinementConfiguration.Profile {
		configurationProvider.change(AIRefinementConfiguration) {
			AIRefinementConfiguration.replaceProfile(it, it.currentProfile(workingMode)) { profile -> update(profile) }
		}
		fireChanged(name, name)
		return configurationProvider.get(AIRefinementConfiguration).currentProfile(workingMode)
	}

	fun addListener(listener: (AIProfile.Name, AIProfile.Name) -> Unit) {
		listeners.add(listener)
	}

	fun fireInitialization() {
		fireChanged(name, name)
	}

	private fun fireChanged(lastName: AIProfile.Name, name: AIProfile.Name) {
		listeners.forEach { it(lastName, name) }
	}
}