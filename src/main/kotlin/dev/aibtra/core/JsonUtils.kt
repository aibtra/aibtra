/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

package dev.aibtra.core

import org.json.simple.JSONObject
import java.io.IOException

class JsonUtils {
	companion object {
		fun <T> objNotNull(input: Any, key: String): T {
			return objMaybeNull<T>(input, key) ?: throw IOException("'$key' does not exist")
		}

		fun <T> objMaybeNull(input: Any, key: String): T? {
			return (input as? JSONObject)?.let {
				val value = it[key]
				if (value == null) {
					null
				}
				else {
					@Suppress("UNCHECKED_CAST")
					(value as? T) ?: throw IOException("'$key' is of wrong type")
				}
			}
		}

		fun formatJson(jsonString: String): String {
			val formattedJson = StringBuilder()
			var indent = ""
			var inQuotes = false

			for (charFromJson in jsonString) {
				when (charFromJson) {
					'"' -> {
						formattedJson.append(charFromJson)
						// Toggle the inQuotes flag if the quote is not escaped
						if (formattedJson.length < 2 || formattedJson[formattedJson.length - 2] != '\\') {
							inQuotes = !inQuotes
						}
					}
					'{', '[' -> {
						formattedJson.append(charFromJson)
						if (!inQuotes) {
							formattedJson.append("\n")
							indent += "    "
							formattedJson.append(indent)
						}
					}
					'}', ']' -> {
						if (!inQuotes) {
							formattedJson.append("\n")
							indent = indent.substring(0, indent.length - 4)
							formattedJson.append(indent)
						}
						formattedJson.append(charFromJson)
					}
					',' -> {
						formattedJson.append(charFromJson)
						if (!inQuotes) {
							formattedJson.append("\n")
							formattedJson.append(indent)
						}
					}
					':' -> {
						formattedJson.append(charFromJson)
						if (!inQuotes) {
							formattedJson.append(" ")
						}
					}
					else -> formattedJson.append(charFromJson)
				}
			}
			return formattedJson.toString()
		}
	}
}