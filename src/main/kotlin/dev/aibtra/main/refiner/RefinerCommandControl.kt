package dev.aibtra.main.refiner

import dev.aibtra.configuration.*
import dev.aibtra.gui.*
import dev.aibtra.ai.*
import dev.aibtra.refiner.*
import java.awt.*
import java.awt.event.*
import java.util.function.*
import java.util.stream.*
import javax.swing.*

class RefinerCommandControl(val refinerDiffManager: RefinerDiffManager, val configurationProvider: ConfigurationProvider) {
	private val beforeArea = JTextArea()
	private val commandArea = JTextArea()
	private val afterArea = JTextArea()
	private val conversationLink = createConversationLink(commandArea)
	private val mainPanel = JPanel()
	private val enterListeners = ArrayList<(ev: KeyEvent) -> Unit>()

	private var profile: AIRefinementConfiguration.Profile? = null
	private var conversation: RefinerConversation? = null

	init {
		val instructionsPanel = JPanel()
		instructionsPanel.layout = BorderLayout()
		instructionsPanel.add(beforeArea, BorderLayout.NORTH)
		instructionsPanel.add(commandArea, BorderLayout.CENTER)
		instructionsPanel.add(afterArea, BorderLayout.SOUTH)

		val linkPanel = JPanel()
		linkPanel.layout = BorderLayout()
		linkPanel.add(conversationLink, BorderLayout.NORTH)

		mainPanel.layout = BorderLayout()
		mainPanel.border = UIManager.getBorder("ScrollPane.border")
		mainPanel.add(instructionsPanel, BorderLayout.CENTER)
		mainPanel.add(linkPanel, BorderLayout.WEST)

		beforeArea.isEnabled = false
		afterArea.isEnabled = false
		setConstantText("", beforeArea)
		setConstantText("", afterArea)
		mainPanel.isVisible = false

		resetCommand()

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

	fun setProfile(profile: AIRefinementConfiguration.Profile?) {
		if (this.profile != profile) {
			if (this.conversation != null) {
				resetCommand()
			}

			this.conversation = null
		}

		this.profile = profile

		val wasVisible = updateInstructions()
		if (!wasVisible) {
			// At least for the startup, it's required to delay this event, otherwise the Profile combobox gets the focus
			Ui.runInEdt {
				commandArea.requestFocusInWindow()
			}
		}
	}

	fun setConversation(conversation: RefinerConversation?) {
		this.conversation = conversation

		updateInstructions()
	}

	fun retrieveCommand(): String {
		val command = commandArea.text
		if (command.isBlank()) {
			return ""
		}

		conversation.let { conversation ->
			if (conversation == null || conversation.isEmpty()) {
				configurationProvider.change(AIRefinementConfiguration) { configuration ->
					val newCommands = configuration.lastCommands.stream().filter { it != command && it.isNotBlank() }.collect(Collectors.toList())
					newCommands.addFirst(command)

					configuration.copy(lastCommands = newCommands)
				}
			}
		}

		return command
	}

	fun getComponent(): JComponent {
		return mainPanel
	}

	fun registerEnterListener(enterListener: (ev: KeyEvent) -> Unit) {
		enterListeners.add(enterListener)
	}

	private fun updateInstructions(): Boolean {
		val split: List<String>? = profile?.let { AIRefinementConfiguration.getCommandInstructions(it, conversation != null) }
		if (split == null || split.size != 2) {
			mainPanel.isVisible = false
			return false
		}

		val wasVisible = mainPanel.isVisible
		setConstantText(split[0], beforeArea)
		setConstantText(split[1], afterArea)
		mainPanel.isVisible = true

		conversation?.let {
			conversationLink.text = composeLabelText(it.entries.size)
		} ?: run {
			conversationLink.text = ""
		}

		return wasVisible
	}

	private fun setConstantText(text: String, textArea: JTextArea) {
		if (text.isNotEmpty()) {
			textArea.text = text.trim().let {
				val lines = it.split("\n")
				if (lines.size > 2) {
					shortenConstantText(lines[0], false) + ELLIPSES
				}
				else {
					shortenConstantText(it, true)
				}
			}

			textArea.isVisible = true
		}
		else {
			textArea.isVisible = false
		}
	}

	private fun resetCommand() {
		configurationProvider.get(AIRefinementConfiguration).lastCommands.firstOrNull()?.let {
			commandArea.text = it
		}
	}

	private fun createConversationLink(commandArea: JTextArea): JLabel {
		val label = JLabel()
		label.font = commandArea.font
		label.border = with(commandArea.margin) {
			BorderFactory.createEmptyBorder(top, left, bottom, right)
		}
		label.verticalAlignment = SwingConstants.TOP
		label.text = composeLabelText(999)
		label.preferredSize = label.preferredSize
		label.text = ""

		label.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				val popupMenu = JPopupMenu()
				JMenuItem("Reset").apply {
					addActionListener {
						refinerDiffManager.updateConversation(null)
					}
					popupMenu.add(this)
				}

				popupMenu.addSeparator()

				conversation?.let { conversation ->
					for (entry in conversation.entries) {
						JMenuItem(entry.title).apply {
							addActionListener {
								refinerDiffManager.updateConversation(conversation.rollbackTo(entry))
							}
							popupMenu.add(this)
						}
					}
					popupMenu.show(e.component, e.x, e.y)
				}
			}
		})
		return label
	}

	companion object {
		private const val CONSTANT_TEXT_LIMIT = 128
		private const val ELLIPSES = " ..."

		fun shortenConstantText(text: String, addEllipsis: Boolean): String {
			if (text.length <= CONSTANT_TEXT_LIMIT) {
				return text
			}

			return text.substring(0, CONSTANT_TEXT_LIMIT) + (if (addEllipsis) ELLIPSES else "")
		}

		private fun composeLabelText(entryCount: Int): String {
			return "<html><a href=''>#$entryCount</a></html>"
		}
	}
}