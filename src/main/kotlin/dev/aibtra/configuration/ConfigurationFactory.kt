/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.configuration

import kotlinx.serialization.KSerializer

interface ConfigurationFactory<D> {
	fun name(): String

	fun serializer(): KSerializer<D>

	fun default(): D

	fun createSerializer(): KSerializer<D> = serializer()
}