package dev.aibtra.ai

enum class AIProvider(val uiName: String, val driver: AIDriver, val apiKeyLink: String?) {
	OPENAI("OpenAI", OpenAIDriver(), "https://platform.openai.com/settings/organization/api-keys"),

	ANTHROPIC("Anthropic", AnthropicDriver(), "https://console.anthropic.com/settings/keys"),

	DEEPSEEK("DeepSeek", DeepSeekDriver(), "https://platform.deepseek.com/api_keys"),

	OLLAMA("Ollama", OllamaDriver(), null);
}