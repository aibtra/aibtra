/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.configuration

import dev.aibtra.core.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class ConfigurationFile<D>(
	private val path: Path,
	private val storable: Boolean,
	private val serializer: KSerializer<D>,
	private var content: D
) {

	var config: D
		get() = content
		set(value) {
			content = value
			store()
		}

	private fun store() {
		if (!storable) {
			LOG.warn("Won't store ${this::class}")
			return
		}

		store(path, content, serializer)
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
		private val json = Json {
			prettyPrint = true
			encodeDefaults = true
			ignoreUnknownKeys = true
		}

		fun <D> load(path: Path, serializer: KSerializer<D>, default: D): ConfigurationFile<D> {
			return try {
				val contentString = Files.readString(path)
				val content = json.decodeFromString(serializer, contentString)
				ConfigurationFile(path, true, serializer, content)
			} catch (e: Exception) {
				when (e) {
					is java.nio.file.NoSuchFileException -> {
						LOG.warn("Configuration file not found: " + e.message)

						try {
							store(path, default, serializer)
						} catch (ex2: Exception) {
							LOG.error(ex2)
						}

						ConfigurationFile(path, true, serializer, default)
					}

					is IOException, is SerializationException -> {
						LOG.error(e)

						ConfigurationFile(path, false, serializer, default)
					}

					else -> throw e
				}
			}
		}

		fun <T> store(path: Path, content: T, serializer: KSerializer<T>) {
			val encoded = json.encodeToString(serializer, content)
			Files.writeString(path, encoded)
		}
	}
}