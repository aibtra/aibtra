package dev.aibtra.ai

import kotlinx.serialization.*

@Suppress("unused")
@Serializable
enum class AIRole(val id: String) {
	USER("user"), SYSTEM("system"), ASSISTANT("assistant")
}