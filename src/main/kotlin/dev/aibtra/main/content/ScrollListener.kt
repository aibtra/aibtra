package dev.aibtra.main.content

import dev.aibtra.diff.*
import dev.aibtra.gui.*

class ScrollListener private constructor(private val leftEditor: AbstractTextEditor, private val rightEditor: AbstractTextEditor, private val scrollState: ScrollState, val keepLeftPosOnRightPosUpdate: () -> Boolean) {
	private var inScrollPosUpdate = false

	companion object {
		fun install(leftEditor: AbstractTextEditor, rightEditor: AbstractTextEditor, scrollState: ScrollState, keepLeftPosOnRightPosUpdate: () -> Boolean): ScrollListener {
			val listener = ScrollListener(leftEditor, rightEditor, scrollState, keepLeftPosOnRightPosUpdate)
			listener.install(listener.leftEditor, Side.LEFT)
			listener.install(listener.rightEditor, Side.RIGHT)

			scrollState.addScrollListener { left, right, mode ->
				Ui.assertEdt()

				leftEditor.scrollTo(left, mode)
				rightEditor.scrollTo(right, mode)
			}

			return listener
		}
	}

	fun scrollLeftToLine(line: Int) {
		inScrollPosUpdate = true
		try {
			leftEditor.scrollToLine(line)
			Side.LEFT.update(scrollState, leftEditor.createScrollPos(), null, false)
			rightEditor.scrollTo(scrollState.syncRightScrollPos(), ScrollState.ScrollMode.FORCE_TOP)
		} finally {
			inScrollPosUpdate = false
		}
	}

	private fun install(editor: AbstractTextEditor, side: Side) {
		var started = false
		editor.addScrollListener { pos, mode ->
			started = started or (pos.top > 0) // E.g., when using simulated text, scrolling the raw area to a specific line may jump back immediately afterward due to a ScrollPaneLayout.layoutContainer event.

			if (inScrollPosUpdate || !started) {
				return@addScrollListener
			}

			inScrollPosUpdate = true
			try {
				side.update(scrollState, pos, mode, keepLeftPosOnRightPosUpdate())
			} finally {
				inScrollPosUpdate = false
			}
		}
	}

	private enum class Side {
		LEFT {
			override fun update(scrollState: ScrollState, pos: ScrollState.ScrollPos, mode: ScrollState.ScrollMode?, keepLeftPos: Boolean) {
				scrollState.updateLeftPos(pos, mode)
			}
		}, RIGHT {
			override fun update(scrollState: ScrollState, pos: ScrollState.ScrollPos, mode: ScrollState.ScrollMode?, keepLeftPos: Boolean) {
				scrollState.updateRightPos(pos, mode, keepLeftPos)
			}
		};

		abstract fun update(scrollState: ScrollState, pos: ScrollState.ScrollPos, mode: ScrollState.ScrollMode?, keepLeftPos: Boolean)
	}
}