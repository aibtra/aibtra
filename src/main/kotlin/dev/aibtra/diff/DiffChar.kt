/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

data class DiffChar(
	val kind: DiffKind,
	val block: DiffBlock?,
	val posRaw: Int,
	val posRef: Int
)