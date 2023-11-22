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
	}
}