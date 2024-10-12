/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.startup

import com.formdev.flatlaf.FlatDarkLaf
import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.configuration.ConfigurationFile
import dev.aibtra.core.GlobalExceptionHandler
import dev.aibtra.core.Logger
import dev.aibtra.core.SingleInstanceAppLock
import dev.aibtra.gui.Ui
import dev.aibtra.gui.Ui.createButton
import dev.aibtra.gui.Ui.toHiDPIPixel
import dev.aibtra.gui.action.DefaultAction
import dev.aibtra.gui.dialogs.DialogDisplayer
import dev.aibtra.gui.dialogs.Dialogs
import dev.aibtra.main.frame.*
import joptsimple.OptionParser
import joptsimple.OptionSet
import kotlinx.coroutines.asCoroutineDispatcher
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.logging.Formatter
import java.util.logging.LogRecord
import javax.swing.*
import kotlin.reflect.KClass

class MainStartup {
	companion object {
		private const val COMMAND_SHOW = "SHOW"

		fun start(paths: ApplicationPaths, args: Array<String>) {
			val parser = OptionParser()
			val backgroundOption = parser.accepts("background")
			val options: OptionSet = parser.parse(*args)
			val backgroundMode = options.has(backgroundOption)

			configureLogging(paths)

			FlatDarkLaf.setup() // required for very early (error dialogs) and to properly initialize the default font size
			GlobalExceptionHandler.install()

			val buildInfo = BuildInfo.load(paths)
			createLogger().info(paths.appName + " build " + buildInfo.sha + " from " + buildInfo.instant)

			val settingsPath = paths.settingsPath
			SingleInstanceAppLock(settingsPath, {
				val coroutineDispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()
				val configurationProvider = ConfigurationProvider(settingsPath, DialogDisplayer.createGlobal())
				val systemTrayEnabled = checkSystemTrayEnabled(configurationProvider)
				val application = MainApplication(paths, configurationProvider, buildInfo, coroutineDispatcher, systemTrayEnabled)
				application.theme.update()

				SwingUtilities.invokeAndWait {
					installTrayIcon(application)
					val hotkeyConfigured = configureHotkey(application)
					if (backgroundMode) {
						createLogger().info("Starting in background")
						if (hotkeyConfigured) {
							showBackgroundFrame()
						}
					}
					else {
						showMainFrame(application)
					}
				}
				application
			}, { command, application ->
				require(command == COMMAND_SHOW)

				showMainFrame(application)
			}).startup(COMMAND_SHOW)
		}

		private fun configureLogging(paths: ApplicationPaths) {
			Logger.setup(paths.settingsPath.resolve("log.txt"))
		}

		private fun showMainFrame(environment: Environment, forceSetClipboard: Boolean = false) {
			Ui.runInEdt {
				val existingFrame = environment.frameManager.getFrame()
				val (frame, setClipboard) = if (existingFrame != null) {
					existingFrame.toFront()
					Pair(existingFrame, forceSetClipboard)
				}
				else {
					val frame = MainFrame(environment)
					frame.show()

					UpdateCheck(environment.buildInfo, environment.configurationProvider, environment.coroutineDispatcher, environment.paths, frame.dialogDisplayer).invoke()
					Pair(frame, true)
				}

				if (setClipboard) {
					frame.setText(getContentFromClipboard(environment.paths))
				}
			}
		}

		private fun showBackgroundFrame() {
			Ui.runInEdt {
				val frame = JFrame(MainFrame.FRAME_TITLE)
				frame.iconImage = Icons.LOGO.getImageIcon(true).image
				frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

				frame.layout = BorderLayout()
				val button = createButton(DefaultAction("Click here to confirm background mode") {
					frame.isVisible = false
				})

				val buttonSize = toHiDPIPixel(128)
				button.preferredSize = Dimension(buttonSize, buttonSize)
				frame.add(button, BorderLayout.CENTER)

				val label = JLabel("Aibtra needs to display a window upon startup, which you must confirm in order to activate the hotkey.")
				val borderSize = toHiDPIPixel(2)
				label.border = BorderFactory.createEmptyBorder(borderSize, borderSize, borderSize, borderSize)
				frame.add(label, BorderLayout.SOUTH)

				frame.pack()

				val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
				val x = (screenSize.width - frame.width) / 2
				val y = (screenSize.height - frame.height) / 2
				frame.setLocation(x, y)

				frame.isVisible = true
			}
		}

		private fun checkSystemTrayEnabled(configurationProvider: ConfigurationProvider): Boolean {
			if (!GuiConfiguration.isSystemTraySupported()) {
				createLogger().error("System tray not supported")
				return false
			}

			if (!configurationProvider.get(GuiConfiguration).systemTray) {
				createLogger().info("System tray not enabled")
				return false
			}

			return true
		}

		private fun installTrayIcon(environment: Environment) {
			if (!environment.systemTrayEnabled) {
				return
			}

			val tray = SystemTray.getSystemTray()
			val popup = JPopupMenu()
			val exitItem = JMenuItem("Exit")
			exitItem.addActionListener {
				environment.frameManager.exit()
			}
			val openAction = JMenuItem("Open")
			openAction.addActionListener {
				showMainFrame(environment)
				popup.isVisible = false
			}
			popup.add(openAction)
			popup.addSeparator()
			popup.add(exitItem)

			val trayIcon = TrayIcon(Icons.LOGO.getImageIcon(true).image)
			trayIcon.isImageAutoSize = true
			trayIcon.addMouseListener(object : MouseAdapter() {
				override fun mouseClicked(e: MouseEvent) {
					if (e.button == MouseEvent.BUTTON1) {
						showMainFrame(environment)
					}
					else if (e.button == MouseEvent.BUTTON3) {
						val location = MouseInfo.getPointerInfo().location
						popup.show(e.component, location.x, location.y)
					}
				}
			})
			try {
				tray.add(trayIcon)
			} catch (e: AWTException) {
				createLogger().error(e)
			}
		}

		private fun configureHotkey(environment: Environment): Boolean {
			return environment.hotkeyListener.configure {
				showMainFrame(environment, true)
			}
		}

		private fun getContentFromClipboard(paths: ApplicationPaths): String {
			paths.getProperty("inputTextFile")?.let {
				return Files.readString(Path.of(it))
			}

			val clipboard = Toolkit.getDefaultToolkit().systemClipboard
			for (tries in 0..3) {
				try {
					if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
						try {
							return clipboard.getData(DataFlavor.stringFlavor) as String
						} catch (e: UnsupportedFlavorException) {
							createLogger().error(e)
						} catch (ioe: IOException) {
							createLogger().error(ioe)
						}
					}
				} catch (e: IllegalStateException) {
					Ui.sleepSafe(50)
				}
			}

			return ""
		}

		private fun createLogger(): Logger {
			return Logger.getLogger(this::class)
		}
	}

	class LogFormatter : Formatter() {
		override fun format(record: LogRecord): String {
			val stringBuilder = StringBuilder()
			stringBuilder.append("${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} [${Thread.currentThread().name}] ${record.level}: ${record.message}\n")

			record.thrown?.let {
				val stringWriter = StringWriter()
				val printWriter = PrintWriter(stringWriter)
				it.printStackTrace(printWriter)
				printWriter.flush()

				stringBuilder.append(stringWriter.toString()).append('\n')
			}

			return stringBuilder.toString()
		}
	}

	class ConfigurationProvider(private val settingsRoot: Path, private val dialogDisplayer: DialogDisplayer) : dev.aibtra.configuration.ConfigurationProvider {
		private val classToConfiguration = HashMap<Class<ConfigurationFactory<Any>>, ConfigurationFile<Any>>()
		private val classToListeners = HashMap<Any, ArrayList<Runnable>>()

		override fun <D> get(factory: ConfigurationFactory<D>): D {
			return getFile(factory).config
		}

		override fun <T> change(factory: ConfigurationFactory<T>, change: (T) -> T) {
			val file = getFile(factory)
			file.config = change(file.config)

			file.config?.let { config ->
				classToListeners[config::class]?.let { listeners ->
					for (runnable in listeners) {
						runnable.run()
					}
				}
			}
		}

		override fun <T : Any> listenTo(clazz: KClass<T>, runnable: Runnable) {
			classToListeners.computeIfAbsent(clazz) { ArrayList() }.add(runnable)
		}

		@Suppress("UNCHECKED_CAST")
		private fun <D> getFile(factory: ConfigurationFactory<D>): ConfigurationFile<D> {
			val javaClass: Class<ConfigurationFactory<Any>> = factory.javaClass as Class<ConfigurationFactory<Any>>
			return classToConfiguration.computeIfAbsent(javaClass) {
				ConfigurationFile.load(settingsRoot.resolve(factory.name() + ".json"), factory.serializer(), factory.default()) {
					Dialogs.showIOError(it, dialogDisplayer)
				} as ConfigurationFile<Any>
			} as ConfigurationFile<D>
		}
	}
}