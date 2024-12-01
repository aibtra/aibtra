package dev.aibtra.main.frame

import dev.aibtra.configuration.*
import dev.aibtra.core.*
import dev.aibtra.gui.*
import dev.aibtra.gui.dialogs.*
import dev.aibtra.main.content.*
import javax.swing.*

class Setup {
	companion object {
		fun show(configurationProvider: ConfigurationProvider, frame: MainFrame, environment: Environment, defaultRunnable: Runnable) {
			val guiConfiguration = configurationProvider.get(GuiConfiguration)
			if (guiConfiguration.setup) {
				defaultRunnable.run()
				return
			}

			OkCancelDialog("Setup") {
				val cols = 3
				val panel = Panel(9, cols)
				val width = if (GuiConfiguration.Fonts.DEFAULT_FONT_SIZE < 16) 400 else 800
				var row = 0
				panel.add(JLabel("<html><body style='width: $width'><b>Welcome to Aibtra! Let's quickly customize the available options.</b></body></html>"), row++, 0, span = cols)

				panel.addSeparatorRow(row++)

				val enableHotkeyCheckbox = JCheckBox("Enable HotKey (recommended)")
				enableHotkeyCheckbox.isSelected = true
				panel.add(enableHotkeyCheckbox, row++, 0)
				row++
				panel.add(Ui.createInfoHtmlLabel("The hotkey is essential for efficient integration into the system. When enabled, pressing ${HotkeyListener.ACCELERATOR_DESCRIPTION} will open Aibtra (assuming it has already been started and is running in the background).", width), row++, 0, span = cols)

				panel.addSeparatorRow(row++)

				val enablePasteOnCloseCheckBox = if (GuiConfiguration.isPasteOnCloseSupported()) {
					JCheckBox("Paste on Close (optional)").apply {
						isSelected = true
						panel.add(this, row++, 0)
						row++
						panel.add(Ui.createInfoHtmlLabel("When using Copy and Close to exit Aibtra, the operating system's focus will typically return to the application from which it was called. When used with the Hotkey, Aibtra can automatically paste the content from the clipboard, saving you from performing a manual Paste operation.", width), row++, 0, span = cols)
					}
				}
				else null

				OkCancelDialog.Content(panel) {
					val hotkeyEnabled = enableHotkeyCheckbox.isSelected
					val pasteOnClose = enablePasteOnCloseCheckBox?.isSelected ?: false
					configurationProvider.change(GuiConfiguration) { guiConfiguration ->
						guiConfiguration.copy(
							hotkeyEnabled = hotkeyEnabled,
							pasteOnClose = pasteOnClose,
							setup = true
						)
					}
					if (hotkeyEnabled) {
						environment.hotkeyListener.update()
					}

					frame.openText("Hello, word!\n\nHit Submit to correct this text.", WorkingMode.CLIPBOARD, null)
				}
			}.show(frame.dialogDisplayer, defaultRunnable)
		}
	}
}