/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.configuration

interface ConfigurationProvider {

	fun <D> get(factory: ConfigurationFactory<D>): D

	fun <T> change(factory: ConfigurationFactory<T>, change: (T) -> T)
}