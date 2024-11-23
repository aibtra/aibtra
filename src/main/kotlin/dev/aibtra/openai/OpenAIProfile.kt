package dev.aibtra.openai

import kotlinx.serialization.*

interface OpenAIProfile {
	val name: Name

	@Serializable
	data class Name(val id: String, val title: String)
}