package dev.aibtra.main.content

import java.awt.event.*

class TextAreaFocusGroup {
	private val listeners = mutableListOf<() -> Unit>()
	private var focused: AbstractTextArea<*>? = null

	fun register(textArea: AbstractTextArea<*>) {
		textArea.addFocusListener(object : FocusListener {
			override fun focusGained(e: FocusEvent?) {
				if (focused != textArea) {
					focused = textArea
					listeners.forEach {
						it()
					}
				}
			}

			override fun focusLost(e: FocusEvent?) {
			}
		})
	}

	fun addListener(listen: () -> Unit) {
		listeners.add(listen)
	}

	fun hasFocus(textArea: AbstractTextArea<*>): Boolean {
		return textArea == focused
	}
}