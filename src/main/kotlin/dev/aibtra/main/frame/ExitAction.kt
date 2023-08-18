/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.frame

class ExitAction(environment: Environment) : MainMenuAction("exit", "Exit", "alt F4", environment.accelerators, {
	environment.frameManager.exit()
})