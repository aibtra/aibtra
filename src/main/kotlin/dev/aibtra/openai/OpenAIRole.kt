package dev.aibtra.openai

import kotlinx.serialization.*

@Suppress("unused")
@Serializable
enum class OpenAIRole(val id: String) {
	USER("user"), SYSTEM("system"), ASSISTANT("assistant")
}