package dev.aibtra.main.resolver

import dev.aibtra.configuration.*
import dev.aibtra.ai.*

class ResolverProfileManager(val configurationProvider: ConfigurationProvider) {

	private val listeners = ArrayList<(AIProfile.Name, AIProfile.Name) -> Unit>()
	private var name: AIProfile.Name = configurationProvider.get(AIResolverConfiguration).currentProfile().name

	fun profile(): AIResolverConfiguration.Profile {
		val configuration = configurationProvider.get(AIResolverConfiguration)
		return configuration.profile(name.id) ?: configuration.currentProfile()
	}

	fun profiles(): List<AIResolverConfiguration.Profile?> {
		val configuration = configurationProvider.get(AIResolverConfiguration)
		return configuration.profiles
	}

	fun setProfile(name: AIProfile.Name) {
		if (name.id == this.name.id) {
			return
		}

		val lastName = this.name
		this.name = name

		configurationProvider.change(AIResolverConfiguration) {
			it.copy(currentProfileId = name.id)
		}

		fireChanged(lastName, name)
	}

	fun addListener(listener: (AIProfile.Name, AIProfile.Name) -> Unit) {
		listeners.add(listener)
	}

	private fun fireChanged(lastName: AIProfile.Name, name: AIProfile.Name) {
		listeners.forEach { it(lastName, name) }
	}
}