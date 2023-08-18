/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

data class DiffBlock(
	val rawFrom: Int,
	val rawTo: Int,
	val refFrom: Int,
	val refTo: Int
)