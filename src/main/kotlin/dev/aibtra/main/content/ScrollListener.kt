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

	private fun install(editor: AbstractTextEditor, side: Side) {
		editor.addScrollListener { pos, mode ->
			if (inScrollPosUpdate) {
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