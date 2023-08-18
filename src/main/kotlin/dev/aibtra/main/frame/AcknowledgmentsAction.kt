/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

import dev.aibtra.gui.dialogs.DialogDisplayer

class AcknowledgmentsAction(
	environment: Environment,
	dialogDisplayer: DialogDisplayer
) : MainMenuAction("acknowledgments", "Acknowledgments", null, environment.accelerators, {
	AboutAction.openUrlInBrowser("https://www.aibtra.dev/acknowledgments", dialogDisplayer)
})