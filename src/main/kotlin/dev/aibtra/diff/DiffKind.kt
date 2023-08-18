/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

enum class DiffKind(val char: Char) {
	EQUAL(' '), ADDED('+'), MODIFIED('~'), REMOVED('-'), GAP_LEFT('>'), GAP_RIGHT('<');

	override fun toString(): String {
		return char.toString()
	}
}