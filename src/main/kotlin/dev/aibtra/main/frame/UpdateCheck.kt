/*
 *
 *  * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 *
 */

@file:UseSerializers(UpdateCheck.Config.LocalDateTimeSerializer::class)

package dev.aibtra.main.frame

import com.formdev.flatlaf.util.*
import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.json.simple.*
import org.json.simple.parser.*
import java.awt.*
import java.io.*
import java.net.*
import java.nio.charset.*
import java.time.*
import java.time.format.*
import java.time.temporal.*
import java.util.*

class UpdateCheck(private val buildInfo: BuildInfo, val configurationProvider: ConfigurationProvider, private val dispatcher: CoroutineDispatcher, private val mainScope: CoroutineScope, private val paths: ApplicationPaths, private val dialogDisplayer: DialogDisplayer) {
	private val coroutineScope = CoroutineScope(Job() + dispatcher)

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

		val bundleType = buildInfo.bundleType ?: return
		val latestSha = try {
			findLatestSha(bundleType)
		} catch (e: Exception) {
			LOG.error(e)
			return
		}

		if (latestSha == null ||
			latestSha == buildInfo.sha) {
			LOG.info("No new version found (latestSha=$latestSha, buildInfo.sha=${buildInfo.sha})")
			return
		}

		LOG.info("Newer version found (latestSha=$latestSha, buildInfo.sha=${buildInfo.sha})")

		val now = LocalDateTime.now()
		if (config.lastFoundSha == latestSha &&
			config.lastCheck?.isAfter(now.minus(config.intervalDays.toLong(), ChronoUnit.DAYS)) == true &&
			paths.getProperty("updateCheck.force") != "true") {
			return
		}

		configurationProvider.change(Config) { it.copy(lastCheck = now, lastFoundSha = latestSha) }

		mainScope.launch(Dispatchers.Main, block = {
			Ui.assertEdt()

			val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
			if (SystemInfo.isWindows) {
				Dialogs.showInfoDialog("New Version", "A new version is available!\n\nExit Aibtra, then run update.bat to upgrade to the new version.", dialogDisplayer)
			}
			else if (SystemInfo.isLinux) {
				Dialogs.showInfoDialog("New Version", "A new version is available!\n\nExit Aibtra, then run update.sh to upgrade to the new version.", dialogDisplayer)
			}
			else if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
				Dialogs.showConfirmationDialog("New Version", "A new version is available!", "Open Browser", dialogDisplayer) {
					desktop.browse(URI(RELEASES_URL))
				}
			}
			else {
				Dialogs.showInfoDialog("New Version", "A new version is available at ${RELEASES_URL}!", dialogDisplayer)
			}
		})
	}

	private fun findLatestSha(bundleType: BuildInfo.BundleType): String? {
		LOG.info("Checking for new version ($bundleType)")
		try {
			return URI(UPDATES_URL).toURL().openConnection().getInputStream().use { stream ->
				val obj = JSONParser().parse(InputStreamReader(stream, StandardCharsets.UTF_8))
				val root = obj as? JSONObject
				root?.get(bundleType.name.lowercase(Locale.getDefault())) as? String
			}
		} catch (e: Exception) {
			LOG.error(e)

			return URI(API_TAGS_URL).toURL().openConnection().getInputStream().use { stream ->
				(JSONParser().parse(InputStreamReader(stream, StandardCharsets.UTF_8)) as? JSONArray)
					?.find { tag -> tag is JSONObject && bundleType.name == JsonUtils.objMaybeNull(tag, "name") }
					?.let { tag -> JsonUtils.objMaybeNull<JSONObject>(tag, "commit") }
					?.let { commit -> JsonUtils.objMaybeNull<String>(commit, "sha") }
			}
		}
	}

	companion object {
		private const val RELEASES_URL = "https://github.com/aibtra/aibtra/releases"
		private const val API_TAGS_URL = "https://api.github.com/repos/aibtra/aibtra/tags"
		private const val UPDATES_URL = "https://updates.aibtra.dev/updates.json"
		private val LOG = Logger.getLogger(this::class)
	}

	@Serializable
	data class Config(
		val lastCheck: LocalDateTime? = null,
		val lastFoundSha: String? = null,
		val intervalDays: Int = 1,
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