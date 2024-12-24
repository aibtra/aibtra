package dev.aibtra.main.content

import java.awt.event.*

class TextEditorFocusGroup {
	private val listeners = mutableListOf<() -> Unit>()
	private var focused: AbstractTextEditor<*>? = null

	fun register(editor: AbstractTextEditor<*>) {
		editor.addFocusListener(object : FocusListener {
			override fun focusGained(e: FocusEvent?) {
				if (focused != editor) {
					focused = editor
					listeners.forEach { it() }
				}
			}

			override fun focusLost(e: FocusEvent?) {
			}
		})
	}

	fun addListener(listen: () -> Unit) {
		listeners.add(listen)
	}

	fun hasFocus(editor: AbstractTextEditor<*>): Boolean {
		return editor == focused
	}
}