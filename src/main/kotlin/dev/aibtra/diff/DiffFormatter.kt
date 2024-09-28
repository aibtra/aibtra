/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

class DiffFormatter(private val mode: Mode) {
	fun format(diff: Diff): Pair<String, List<DiffChar>> {
		val raw = diff.raw
		val rawTo = diff.rawTo
		val ref = diff.ref
		val blocks = diff.blocks
		if (ref.isEmpty() && blocks.isEmpty()) {
			return when (mode) {
				Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED -> {
					Pair(ref, List(ref.length) { pos: Int ->
						DiffChar(DiffKind.EQUAL, null, pos, pos)
					})
				}

				Mode.KEEP_RAW_FOR_MODIFIED -> {
					Pair(raw, List(raw.length) { pos: Int ->
						DiffChar(DiffKind.EQUAL, null, pos, pos)
					})
				}

				Mode.KEEP_REF_FOR_MODIFIED -> {
					Pair(ref, List(ref.length) { pos: Int ->
						DiffChar(DiffKind.EQUAL, null, pos, pos)
					})
				}
			}
		}

		val formatted = StringBuilder()
		val chars: MutableList<DiffChar> = mutableListOf()
		var rawFrom = 0
		var refFrom = 0
		for (block in blocks) {
			appendEqualBlock(raw, rawFrom, block.rawFrom, ref, refFrom, block.refFrom, formatted, chars)

			when (mode) {
				Mode.REPLACE_MODIFIED_BY_ADDED_REMOVED -> {
					val removed = raw.substring(block.rawFrom, block.rawTo)
					formatted.append(removed)
					chars.addAll(List(removed.length) { i -> DiffChar(DiffKind.REMOVED, block, block.rawFrom + i, Math.min(block.refFrom, block.refTo - 1)) })

					val added = ref.substring(block.refFrom, block.refTo)
					formatted.append(added)
					chars.addAll(List(added.length) { i -> DiffChar(DiffKind.ADDED, block, block.rawTo, block.refFrom + i) })
				}

				Mode.KEEP_RAW_FOR_MODIFIED -> {
					val kind = if (block.refFrom < block.refTo) DiffKind.MODIFIED else DiffKind.REMOVED
					val text = raw.substring(block.rawFrom, block.rawTo)
					formatted.append(text)
					chars.addAll(List(text.length) { i -> DiffChar(kind, block, block.rawFrom + i, Math.min(block.refFrom, block.refTo - 1)) })
				}

				Mode.KEEP_REF_FOR_MODIFIED -> {
					val kind = if (block.rawFrom < block.rawTo) DiffKind.MODIFIED else DiffKind.ADDED
					val text = ref.substring(block.refFrom, block.refTo)
					formatted.append(text)
					chars.addAll(List(text.length) { i -> DiffChar(kind, block, Math.min(block.rawFrom, block.rawTo - 1), block.refFrom + i) })
				}
			}

			rawFrom = block.rawTo
			refFrom = block.refTo
		}

		appendEqualBlock(raw, rawFrom, rawTo, ref, refFrom, ref.length, formatted, chars)

		if (mode == Mode.KEEP_RAW_FOR_MODIFIED) {
			for (block in blocks) {
				if (block.rawFrom == block.rawTo) {
					if (block.rawFrom > 0) {
						chars[block.rawFrom - 1] = DiffChar(DiffKind.GAP_LEFT, block, block.rawFrom - 1, block.refFrom)
					}
					if (block.rawFrom < chars.size) {
						chars[block.rawFrom] = DiffChar(DiffKind.GAP_RIGHT, block, block.rawFrom, block.refFrom)
					}
				}
			}

			formatted.append(raw.substring(rawTo))
			chars.addAll(List(raw.length - rawTo) { i -> DiffChar(DiffKind.EQUAL, null, rawTo + i, ref.length - 1) })
		}
		else if (mode == Mode.KEEP_REF_FOR_MODIFIED) {
			for (block in blocks) {
				if (block.refFrom == block.refTo) {
					if (block.refFrom > 0) {
						chars[block.refFrom - 1] = DiffChar(DiffKind.GAP_LEFT, block, block.rawFrom, block.refFrom - 1)
					}
					if (block.refFrom < chars.size) {
						chars[block.refFrom] = DiffChar(DiffKind.GAP_RIGHT, block, block.rawFrom, block.refFrom)
					}
				}
			}
		}

		require(formatted.length == chars.size)
		return Pair(formatted.toString(), chars)
	}

	private fun appendEqualBlock(raw: String, rawFrom: Int, rawTo: Int, ref: String, refFrom: Int, refTo: Int, formatted: StringBuilder, chars: MutableList<DiffChar>) {
		val equal = raw.substring(rawFrom, rawTo)
		require(equal == ref.substring(refFrom, refTo))

		if (equal.isEmpty()) {
			return
		}

		formatted.append(equal)
		chars.addAll(List(equal.length) { i -> DiffChar(DiffKind.EQUAL, null, rawFrom + i, refFrom + i) })
	}

	enum class Mode {
		REPLACE_MODIFIED_BY_ADDED_REMOVED, KEEP_RAW_FOR_MODIFIED, KEEP_REF_FOR_MODIFIED
	}
}