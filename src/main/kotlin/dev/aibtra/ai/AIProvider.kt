package dev.aibtra.ai

enum class AIProvider(val uiName: String, val driver: AIDriver, val apiKeyLink: String) {
	OPENAI("OpenAI", OpenAIDriver(), "https://platform.openai.com/settings/organization/api-keys");
}