package dev.aibtra.ai

import kotlinx.serialization.*

interface AIProfile {
	val provider: AIProvider

	val name: Name

	@Serializable
	data class Name(val id: String, val title: String)
}