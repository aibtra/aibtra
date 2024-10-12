package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.openai.OpenAIConfiguration

class ProfileManager(val configurationProvider: ConfigurationProvider) {

	private val listeners = ArrayList<(OpenAIConfiguration.Profile.Name, OpenAIConfiguration.Profile.Name) -> Unit>()

	private var name: OpenAIConfiguration.Profile.Name = configurationProvider.get(OpenAIConfiguration).currentProfile().name

	fun profile(): OpenAIConfiguration.Profile {
		val configuration = configurationProvider.get(OpenAIConfiguration)
		return configuration.profile(name.id) ?: configuration.currentProfile()
	}

	fun setProfile(name: OpenAIConfiguration.Profile.Name) {
		if (name.id == this.name.id) {
			return
		}

		val lastName = this.name
		this.name = name

		configurationProvider.change(OpenAIConfiguration) {
			it.copy(defaultProfileId = name.id)
		}

		fireChanged(lastName, name)
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