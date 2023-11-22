/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

@file:UseSerializers(UpdateCheck.Config.LocalDateTimeSerializer::class)

package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.core.JsonUtils
import dev.aibtra.core.Logger
import dev.aibtra.gui.Ui
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.awt.Desktop
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

class UpdateCheck(private val buildInfo: BuildInfo, val configurationProvider: ConfigurationProvider, private val dispatcher: CoroutineDispatcher, private val dialogDisplayer: DialogDisplayer) {
	private val coroutineScope = CoroutineScope(Job() + dispatcher)
	private val mainScope = MainScope()

	fun invoke() {
		coroutineScope.launch(dispatcher) {
			runCheck()
		}
	}

	private fun runCheck() {
		if (buildInfo.sha.length < 40) {
			return
		}

		val config = configurationProvider.get(Config)
		if (!config.enabled) {
			return
		}

		val now = LocalDateTime.now()
		if (config.lastCheck?.isAfter(now.minus(config.intervalDays.toLong(), ChronoUnit.DAYS)) == true) {
			return
		}

		val latestSha = try {
			findLatestSha()
		} catch (e: Exception) {
			LOG.error(e)
			return
		}

		configurationProvider.change(Config) { it.copy(lastCheck = now) }

		if (latestSha == null ||
			latestSha == buildInfo.sha) {
			return
		}

		mainScope.launch(Dispatchers.Main, block = {
			Ui.assertEdt()

			val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
			if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
				Dialogs.showConfirmationDialog("New Version", "A new version is available!", "Open Browser", dialogDisplayer) {
					desktop.browse(URI(RELEASES_URL))
				}
			}
			else {
				Dialogs.showInfoDialog("New Version", "A new version is available at ${RELEASES_URL}!", dialogDisplayer)
			}
		})
	}

	private fun findLatestSha(): String? {
		return URL(API_TAGS_URL).openConnection().getInputStream().use { stream ->
			(JSONParser().parse(InputStreamReader(stream, StandardCharsets.UTF_8)) as? JSONArray)
				?.find { tag -> tag is JSONObject && "latest" == JsonUtils.objMaybeNull(tag, "name") }
				?.let { tag -> JsonUtils.objMaybeNull<JSONObject>(tag, "commit") }
				?.let { commit -> JsonUtils.objMaybeNull<String>(commit, "sha") }
		}
	}

	companion object {
		private const val RELEASES_URL = "https://github.com/aibtra/aibtra/releases"
		private const val API_TAGS_URL = "https://api.github.com/repos/aibtra/aibtra/tags"
		private val LOG = Logger.getLogger(this::class)
	}

	@Serializable
	data class Config(
		val lastCheck: LocalDateTime? = null,
		val intervalDays: Int = 7,
		val enabled: Boolean = true
	) {
		companion object : ConfigurationFactory<Config> {
			override fun name(): String = "update-check"

			override fun default(): Config {
				return Config()
			}
		}

		object LocalDateTimeSerializer : KSerializer<LocalDateTime?> {
			override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

			override fun serialize(encoder: Encoder, value: LocalDateTime?) {
				encoder.encodeString(value?.let {
					DateTimeFormatter.ISO_DATE_TIME.format(value)
				} ?: "")
			}

			override fun deserialize(decoder: Decoder): LocalDateTime? {
				return try {
					val value = decoder.decodeString()
					LocalDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(value))
				} catch (e: DateTimeParseException) {
					LOG.error(e.message ?: "Error parsing LocalDateTime")
					null
				}
			}
		}
	}
}