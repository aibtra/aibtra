/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.configuration

import dev.aibtra.core.*
import kotlinx.serialization.*

interface ConfigurationFactory<D> {
	fun name(): String

	fun serializer(): KSerializer<D>

	fun default(): D

	fun createSerializer(): KSerializer<D> = serializer()

	companion object {
		internal lateinit var paths: ApplicationPaths

		fun initialize(paths: ApplicationPaths) {
			this.paths = paths
		}
	}
}