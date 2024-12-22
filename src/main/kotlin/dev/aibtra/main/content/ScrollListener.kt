package dev.aibtra.main.content

import dev.aibtra.diff.*
import dev.aibtra.gui.*

class ScrollListener(private val scrollState: ScrollState, val keepLeftPosOnRightPosUpdate: () -> Boolean) {
	private var inScrollPosUpdate = false

	fun install(leftTextArea: AbstractTextArea<*>, rightTextArea: AbstractTextArea<*>) {
		install(leftTextArea, Side.LEFT)
		install(rightTextArea, Side.RIGHT)

		scrollState.addScrollListener { left, right ->
			Ui.assertEdt()

			leftTextArea.scrollTo(left)
			rightTextArea.scrollTo(right)
		}
	}

	private fun install(textArea: AbstractTextArea<*>, side: Side) {
		textArea.addScrollListener { pos ->
			if (inScrollPosUpdate) {
				return@addScrollListener
			}

			inScrollPosUpdate = true
			try {
				side.update(scrollState, pos, keepLeftPosOnRightPosUpdate())
			} finally {
				inScrollPosUpdate = false
			}
		}
	}

	private enum class Side {
		LEFT {
			override fun update(scrollState: ScrollState, pos: ScrollState.ScrollPos, keepLeftPos: Boolean) {
				scrollState.updateLeftPos(pos)
			}
		}, RIGHT {
			override fun update(scrollState: ScrollState, pos: ScrollState.ScrollPos, keepLeftPos: Boolean) {
				scrollState.updateRightPos(pos, keepLeftPos)
			}
		};

		abstract fun update(scrollState: ScrollState, pos: ScrollState.ScrollPos, keepLeftPos: Boolean)
	}
}