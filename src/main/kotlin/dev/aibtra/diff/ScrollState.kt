/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import dev.aibtra.core.*
import dev.aibtra.gui.*
import java.util.function.*
import kotlin.math.*

class ScrollState {
	private val scrollListeners = ArrayList<(left: ScrollPos, right: ScrollPos, mode: ScrollMode) -> Unit>()

	private var inScrollPosUpdate = false
	private var state: State = INITIAL

	fun updateLeftText(leftText: String) {
		Ui.assertEdt()

		val leftMax = leftText.length - 1
		val pos = state.leftPos
		val newPos = if (pos.bottom > leftMax) {
			ScrollPos(max(0, leftMax - (pos.bottom - pos.top)), leftMax)
		}
		else {
			pos
		}

		state = state.copy(leftText = leftText, leftPos = newPos)
	}

	fun updateRightText(rightText: String) {
		Ui.assertEdt()

		val rightMax = rightText.length - 1
		val pos = state.rightPos
		val newPos = if (pos.bottom > rightMax) {
			ScrollPos(max(0, rightMax - (pos.bottom - pos.top)), rightMax)
		}
		else {
			pos
		}

		state = state.copy(rightText = rightText, rightPos = newPos)
	}

	fun updateDiffBlocks(blocks: List<DiffBlock>) {
		Ui.assertEdt()

		state = state.copy(blocks = blocks)
		doSyncRightScrollPos()
	}

	fun updateLeftPos(leftPos: ScrollPos, mode: ScrollMode?) {
		Ui.assertEdt()

		runUpdateScrollPos(Consumer {
			if (it.leftPos == leftPos && (mode == null || it.mode == mode)) {
				return@Consumer
			}

			val rightPos = mapScrollPos(it, leftPos,
				{ s -> s.leftText },
				{ s -> s.rightText },
				{ b -> b.rawFrom },
				{ b -> b.refFrom },
				{ b -> b.rawTo },
				{ b -> b.refTo })
			updateScrollPos(leftPos, rightPos, mode, false)
		})
	}

	fun updateRightPos(rightPos: ScrollPos, mode: ScrollMode?, keepLeftPos : Boolean) {
		Ui.assertEdt()

		runUpdateScrollPos(Consumer {
			if (it.rightPos == rightPos && (mode == null || it.mode == mode)) {
				return@Consumer
			}

			val leftScrollPos = if (keepLeftPos) {
				state.leftPos
			}
			else {
				mapScrollPos(it, rightPos,
					{ s -> s.rightText },
					{ s -> s.leftText },
					{ b -> b.refFrom },
					{ b -> b.rawFrom },
					{ b -> b.refTo },
					{ b -> b.rawTo })
			}
			updateScrollPos(leftScrollPos, rightPos, mode, false)
		})
	}

	fun syncRightScrollPos() : ScrollPos {
		Ui.assertEdt()

		return doSyncRightScrollPos()
	}

	fun addScrollListener(listener: (left: ScrollPos, right: ScrollPos, mode: ScrollMode) -> Unit) {
		Ui.assertEdt()

		scrollListeners.add(listener)
	}

	private fun doSyncRightScrollPos(): ScrollPos {
		val leftPos = state.leftPos
		val rightPos = mapScrollPos(state, leftPos,
			{ s -> s.leftText },
			{ s -> s.rightText },
			{ b -> b.rawFrom },
			{ b -> b.refFrom },
			{ b -> b.rawTo },
			{ b -> b.refTo })
		state = state.copy(rightPos = rightPos)
		return rightPos
	}

	private fun runUpdateScrollPos(consumer: Consumer<State>) {
		require(!inScrollPosUpdate)

		inScrollPosUpdate = true
		try {
			state.let {
				consumer.accept(it)
			}
		} finally {
			inScrollPosUpdate = false
		}
	}

	private fun updateScrollPos(leftPosRaw: ScrollPos, rightPosRaw: ScrollPos, rawMode: ScrollMode?, rightToLeft: Boolean) {
		val leftPos = if (leftPosRaw.bottom > state.leftText.length) state.leftPos else leftPosRaw
		val rightPos = if (rightPosRaw.bottom > state.rightText.length) state.rightPos else rightPosRaw
		if (leftPos == state.leftPos && rightPos == state.rightPos && (rawMode == null || rawMode == state.mode)) {
			return
		}

		val mode = rawMode ?: state.mode

		LOG.debug("SCROLL: $leftPos ${if (rightToLeft) "<--" else "-->"} $rightPos (mode=${mode}${rawMode?.let { ", force" } ?: ""})")

		state = state.copy(leftPos = leftPos, rightPos = rightPos, mode = mode)
		scrollListeners.forEach { it(leftPos, rightPos, mode) }
	}

	private fun mapScrollPos(
		state: State,
		pos: ScrollPos,
		srcText: (state: State) -> String, dstText: (state: State) -> String,
		srcFrom: (block: DiffBlock) -> Int, dstFrom: (block: DiffBlock) -> Int,
		srcTo: (block: DiffBlock) -> Int, dstTo: (block: DiffBlock) -> Int
	): ScrollPos {
		return ScrollPos(
			mapScrollPos(state, pos.top, srcText, dstText, srcFrom, dstFrom, srcTo, dstTo, "TOP"),
			mapScrollPos(state, pos.bottom, srcText, dstText, srcFrom, dstFrom, srcTo, dstTo, "BOTTOM")
		)
	}

	@Suppress("NAME_SHADOWING")
	private fun mapScrollPos(
		state: State,
		srcPos: Int,
		srcText: (state: State) -> String, dstText: (state: State) -> String,
		srcFrom: (block: DiffBlock) -> Int, dstFrom: (block: DiffBlock) -> Int,
		srcTo: (block: DiffBlock) -> Int, dstTo: (block: DiffBlock) -> Int,
		debugTitle: String
	): Int {
		val srcMax = srcText(state).length - 1
		val dstMax = dstText(state).length - 1
		val beforeIndex = state.blocks.binarySearch {
			srcFrom(it).compareTo(srcPos)
		}.let {
			if (it < 0) -it - 2 else it
		}

		if (beforeIndex < 0) {
			LOG.trace("$debugTitle: $srcPos -> $srcPos (beforeIndex < 0)")
			return srcPos
		}

		if (beforeIndex >= state.blocks.size) {
			val pos = dstMax - Math.max(0, Math.min(dstMax - (srcMax - srcPos), dstMax))
			LOG.trace("$debugTitle: $srcPos -> $pos (beforeIndex >= state.blocks.size)")
			return pos
		}

		val lowerBlock = state.blocks[beforeIndex]
		val srcTo = srcTo(lowerBlock)
		val dstTo = dstTo(lowerBlock)
		if (srcPos >= srcTo) {
			val pos = Math.min(dstTo + (srcPos - srcTo), dstMax)
			LOG.trace("$debugTitle: $srcPos -> $pos (srcPos >= srcTo)")
			return pos
		}

		val srcFrom = srcFrom(lowerBlock)
		val dstFrom = dstFrom(lowerBlock)
		require(srcPos >= srcFrom)

		val ratio = (srcPos - srcFrom) / (srcTo - srcFrom).toDouble()
		val pos = dstFrom + ((dstTo - dstFrom) * ratio).toInt()
		LOG.trace("$debugTitle: $srcPos -> $pos (ratio=$ratio)")
		return pos
	}

	data class State(val leftText: String, val leftPos: ScrollPos, val rightText: String, val rightPos: ScrollPos, val mode: ScrollMode, val blocks: List<DiffBlock>)

	data class ScrollPos(val top: Int, val bottom: Int) {
		companion object {
			val INITIAL = ScrollPos(0, 0)
		}
	}

	enum class ScrollMode {
		FORCE_TOP, FORCE_BOTTOM
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)

		val INITIAL = State("", ScrollPos.INITIAL, "", ScrollPos.INITIAL, ScrollMode.FORCE_TOP, listOf())
	}
}