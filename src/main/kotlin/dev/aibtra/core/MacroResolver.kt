package dev.aibtra.core

import java.io.*

class MacroResolver(val replacement: (String) -> String?) {

	fun replace(text: String, map: MutableMap<String, String>? = null) : String {
		return KEYWORD_REGEX.replace(text) { matchResult ->
			val macro = matchResult.groupValues[0]
			val value = replacement(macro) ?: throw IOException("Unknown keyword '$macro'")
			map?.put(macro, value)
			value
		}
	}

	companion object {
		val KEYWORD_REGEX = "\\$\\{(\\w+)}".toRegex()
	}
}