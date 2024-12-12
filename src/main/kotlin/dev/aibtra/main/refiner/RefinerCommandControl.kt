package dev.aibtra.main.refiner

import dev.aibtra.configuration.*
import dev.aibtra.gui.*
import dev.aibtra.openai.*
import java.awt.*
import java.awt.event.*
import java.util.function.*
import java.util.stream.*
import javax.swing.*

class RefinerCommandControl(val configurationProvider: ConfigurationProvider) {
	private val beforeArea = JTextArea()
	private val commandArea = JTextArea()
	private val afterArea = JTextArea()
	private val panel = JPanel()
	private val enterListeners = ArrayList<(ev: KeyEvent) -> Unit>()

	private var profile : OpenAIRefinementConfiguration.Profile? = null

	init {
		panel.layout = BorderLayout()
		panel.border = UIManager.getBorder("ScrollPane.border")
		panel.add(beforeArea, BorderLayout.NORTH)
		panel.add(commandArea, BorderLayout.CENTER)
		panel.add(afterArea, BorderLayout.SOUTH)

		beforeArea.isEnabled = false
		afterArea.isEnabled = false
		setConstantText("", beforeArea)
		setConstantText("", afterArea)
		panel.isVisible = false

		configurationProvider.get(OpenAIRefinementConfiguration).lastCommands.firstOrNull()?.let {
			commandArea.text = it
		}

		commandArea.addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				when {
					e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown -> {
						commandArea.insert("\n", commandArea.caretPosition)
						e.consume()
					}
					e.keyCode == KeyEvent.VK_ENTER -> {
						enterListeners.forEach(Consumer { it(e) })
					}
				}
			}
		})
	}

	fun setProfile(profile: OpenAIRefinementConfiguration.Profile?) {
		this.profile = profile

		val split: List<String>? = profile?.let { OpenAIRefinementConfiguration.getCommandInstructions(it) }
		if (split == null || split.size != 2) {
			panel.isVisible = false
			return
		}

		val wasVisible = panel.isVisible
		setConstantText(split[0].trim(), beforeArea)
		setConstantText(split[1].trim(), afterArea)
		panel.isVisible = true

		if (!wasVisible) {
			// At least for the startup, it's required to delay this event, otherwise the Profile combobox gets the focus
			Ui.runInEdt {
				commandArea.requestFocusInWindow()
			}
		}
	}

	fun retrieveCommand() : String {
		val command = commandArea.text
		if (command.isBlank()) {
			return ""
		}

		configurationProvider.change(OpenAIRefinementConfiguration) { configuration ->
			val newCommands = configuration.lastCommands.stream().filter { it != command && it.isNotBlank() }.collect(Collectors.toList())
			newCommands.addFirst(command)

			configuration.copy(lastCommands = newCommands)
		}

		return command
	}

	fun getComponent(): JComponent {
		return panel
	}

	fun registerEnterListener(enterListener: (ev: KeyEvent) -> Unit) {
		enterListeners.add(enterListener)
	}

	private fun setConstantText(text: String, textArea: JTextArea) {
		if (text.isNotEmpty()) {
			textArea.text = text
			textArea.isVisible = true
		}
		else {
			textArea.isVisible = false
		}
	}
}