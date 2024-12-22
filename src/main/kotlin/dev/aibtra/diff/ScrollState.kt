/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.diff

import dev.aibtra.gui.*
import java.util.function.*
import kotlin.math.*

class ScrollState {
	private val scrollListeners = ArrayList<(left: ScrollPos, right: ScrollPos) -> Unit>()

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

	fun updateLeftPos(leftPos: ScrollPos) {
		Ui.assertEdt()

		runUpdateScrollPos(Consumer {
			if (it.leftPos == leftPos) {
				return@Consumer
			}

			val rightPos = mapScrollPos(it, leftPos,
				{ s -> s.leftText },
				{ s -> s.rightText },
				{ b -> b.rawFrom },
				{ b -> b.refFrom },
				{ b -> b.rawTo },
				{ b -> b.refTo })
			updateScrollPos(leftPos, rightPos)
		})
	}

	fun updateRightPos(rightPos: ScrollPos, keepLeftPos : Boolean) {
		Ui.assertEdt()

		runUpdateScrollPos(Consumer {
			if (it.rightPos == rightPos) {
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
			updateScrollPos(leftScrollPos, rightPos)
		})
	}

	fun syncRightScrollPos() : ScrollPos {
		Ui.assertEdt()

		return doSyncRightScrollPos()
	}

	fun addScrollListener(listener: (left: ScrollPos, right: ScrollPos) -> Unit) {
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

	private fun updateScrollPos(leftPosRaw: ScrollPos, rightPosRaw: ScrollPos) {
		val leftPos = if (leftPosRaw.bottom > state.leftText.length) state.leftPos else leftPosRaw
		val rightPos = if (rightPosRaw.bottom > state.rightText.length) state.rightPos else rightPosRaw
		if (leftPos == state.leftPos && rightPos == state.rightPos) {
			return
		}

		state = state.copy(leftPos = leftPos, rightPos = rightPos)
		scrollListeners.toList().forEach { it(leftPos, rightPos) }
	}

	private fun mapScrollPos(
		state: State,
		pos: ScrollPos,
		srcText: (state: State) -> String, dstText: (state: State) -> String,
		srcFrom: (block: DiffBlock) -> Int, dstFrom: (block: DiffBlock) -> Int,
		srcTo: (block: DiffBlock) -> Int, dstTo: (block: DiffBlock) -> Int
	): ScrollPos {
		return ScrollPos(
			mapScrollPos(state, pos.top, srcText, dstText, srcFrom, dstFrom, srcTo, dstTo),
			mapScrollPos(state, pos.bottom, srcText, dstText, srcFrom, dstFrom, srcTo, dstTo)
		)
	}

	@Suppress("NAME_SHADOWING")
	private fun mapScrollPos(
		state: State,
		srcPos: Int,
		srcText: (state: State) -> String, dstText: (state: State) -> String,
		srcFrom: (block: DiffBlock) -> Int, dstFrom: (block: DiffBlock) -> Int,
		srcTo: (block: DiffBlock) -> Int, dstTo: (block: DiffBlock) -> Int
	): Int {
		val srcMax = srcText(state).length - 1
		val dstMax = dstText(state).length - 1
		val beforeIndex = state.blocks.binarySearch {
			srcFrom(it).compareTo(srcPos)
		}.let {
			if (it < 0) -it - 2 else it
		}

		if (beforeIndex < 0) {
			return srcPos
		}

		if (beforeIndex >= state.blocks.size) {
			return dstMax - Math.max(0, Math.min(dstMax - (srcMax - srcPos), dstMax))
		}

		val lowerBlock = state.blocks[beforeIndex]
		val srcTo = srcTo(lowerBlock)
		val dstTo = dstTo(lowerBlock)
		if (srcPos >= srcTo) {
			return Math.min(dstTo + (srcPos - srcTo), dstMax)
		}

		val srcFrom = srcFrom(lowerBlock)
		val dstFrom = dstFrom(lowerBlock)
		require(srcPos >= srcFrom)

		val ratio = (srcPos - srcFrom) / (srcTo - srcFrom).toDouble()
		return dstFrom + ((dstTo - dstFrom) * ratio).toInt()
	}

	data class State(val leftText: String, val leftPos: ScrollPos, val rightText: String, val rightPos: ScrollPos, val blocks: List<DiffBlock>)

	data class ScrollPos(val top: Int, val bottom: Int) {
		companion object {
			val INITIAL = ScrollPos(0, 0)
		}
	}

	companion object {
		val INITIAL = State("", ScrollPos.INITIAL, "", ScrollPos.INITIAL, listOf())
	}
}