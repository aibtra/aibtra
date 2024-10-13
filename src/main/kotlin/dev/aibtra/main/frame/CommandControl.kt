package dev.aibtra.main.frame

import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.openai.OpenAIConfiguration
import dev.aibtra.openai.OpenAIConfiguration.Profile
import java.awt.BorderLayout
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager

class CommandControl(val configurationProvider: ConfigurationProvider) {
	private val beforeArea = JTextArea()
	private val commandArea = JTextArea()
	private val afterArea = JTextArea()
	private val panel = JPanel()

	private var profile : Profile? = null

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

		configurationProvider.get(OpenAIConfiguration).lastCommands.firstOrNull()?.let {
			commandArea.text = it
		}
	}

	fun setProfile(profile: Profile?) {
		this.profile = profile

		val split: List<String>? = profile?.let { OpenAIConfiguration.getCommandInstructions(it) }
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
			SwingUtilities.invokeLater {
				commandArea.requestFocusInWindow()
			}
		}
	}

	fun retrieveCommand() : String {
		val command = commandArea.text
		if (command.isBlank()) {
			return ""
		}

		configurationProvider.change(OpenAIConfiguration) {
			val newCommands = it.lastCommands.stream().filter { it != command && it.isNotBlank() }.collect(Collectors.toList())
			newCommands.addFirst(command)

			it.copy(lastCommands = newCommands)
		}

		return command
	}

	fun getComponent(): JComponent {
		return panel
	}

	private fun setConstantText(text: String, textArea: JTextArea) {
		if (text.length > 0) {
			textArea.text = text
			textArea.isVisible = true
		}
		else {
			textArea.isVisible = false
		}
	}
}