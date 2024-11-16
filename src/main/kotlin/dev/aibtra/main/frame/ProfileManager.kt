package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.core.WorkingMode
import dev.aibtra.openai.OpenAIProfiles

class ProfileManager(private val workingMode: WorkingMode, val configurationProvider: ConfigurationProvider) {

	private val listeners = ArrayList<(OpenAIProfiles.Profile.Name, OpenAIProfiles.Profile.Name) -> Unit>()
	private var name: OpenAIProfiles.Profile.Name = configurationProvider.get(OpenAIProfiles).currentProfile(workingMode).name

	fun profile(): OpenAIProfiles.Profile {
		val configuration = configurationProvider.get(OpenAIProfiles)
		return configuration.profile(name.id) ?: configuration.currentProfile(workingMode)
	}

	fun profiles(): List<OpenAIProfiles.Profile?> {
		val configuration = configurationProvider.get(OpenAIProfiles)
		return configuration.profiles
	}

	fun getProfile(name: OpenAIProfiles.Profile.Name): OpenAIProfiles.Profile? {
		val configuration = configurationProvider.get(OpenAIProfiles)
		return configuration.profile(name.id)
	}

	fun setProfile(name: OpenAIProfiles.Profile.Name) {
		if (name.id == this.name.id) {
			return
		}

		val lastName = this.name
		this.name = name

		configurationProvider.change(OpenAIProfiles) {
			it.copy(workingModeToDefaultProfileId = it.workingModeToDefaultProfileId.plus(workingMode to name.id))
		}

		fireChanged(lastName, name)
	}

	fun overrideProfile(id: String) {
		val name = configurationProvider.get(OpenAIProfiles).profile(id)?.name
		if (name == null || name.id == this.name.id) {
			return
		}

		this.name = name

		fireChanged(name, name)
	}

	fun updateCurrentProfile(update: (OpenAIProfiles.Profile) -> OpenAIProfiles.Profile): OpenAIProfiles.Profile {
		configurationProvider.change(OpenAIProfiles) {
			OpenAIProfiles.replaceProfile(it, it.currentProfile(workingMode)) { profile -> update(profile) }
		}
		fireChanged(name, name)
		return configurationProvider.get(OpenAIProfiles).currentProfile(workingMode)
	}

	fun addListener(listener: (OpenAIProfiles.Profile.Name, OpenAIProfiles.Profile.Name) -> Unit) {
		listeners.add(listener)
	}

	fun fireInitialization() {
		fireChanged(name, name)
	}

	private fun fireChanged(lastName: OpenAIProfiles.Profile.Name, name: OpenAIProfiles.Profile.Name) {
		listeners.forEach { it(lastName, name) }
	}
}