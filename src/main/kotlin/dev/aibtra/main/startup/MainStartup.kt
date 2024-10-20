/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.main.startup

import com.formdev.flatlaf.FlatDarkLaf
import dev.aibtra.configuration.ConfigurationFactory
import dev.aibtra.configuration.ConfigurationFile
import dev.aibtra.configuration.ConfigurationProvider
import dev.aibtra.core.GlobalExceptionHandler
import dev.aibtra.core.Logger
import dev.aibtra.core.SingleInstanceAppLock
import dev.aibtra.core.WorkingMode
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
import java.nio.file.StandardCopyOption
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
			configureLogging(paths)

			FlatDarkLaf.setup() // required for very early (error dialogs) and to properly initialize the default font size
			GlobalExceptionHandler.install()

			val buildInfo = BuildInfo.load(paths)
			createLogger().info(paths.appName + " build " + buildInfo.sha + " from " + buildInfo.instant)

			val settingsPath = paths.settingsPath
			SingleInstanceAppLock(settingsPath, {
				val coroutineDispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()
				val configurationProvider = ConfigurationProviderImpl(settingsPath, DialogDisplayer.createGlobal())
				val systemTrayEnabled = checkSystemTrayEnabled(configurationProvider)
				val application = MainApplication(paths, configurationProvider, buildInfo, coroutineDispatcher, systemTrayEnabled)
				application.theme.update()

				val arguments = Arguments(args.toList())
				SwingUtilities.invokeAndWait {
					installTrayIcon(application)
					val hotkeyConfigured = configureHotkey(application)
					if (arguments.options.backgroundMode) {
						createLogger().info("Starting in background")
						if (hotkeyConfigured) {
							showBackgroundFrame()
						}
					}
					else {
						invoke(arguments, application)
					}
				}
				application
			}, { command, arguments, application ->
				require(command == COMMAND_SHOW)

				invoke(Arguments(arguments), application)
			}).startup(COMMAND_SHOW, args.asList())
		}

		private fun configureLogging(paths: ApplicationPaths) {
			Logger.setup(paths.settingsPath.resolve("log.txt"))
		}

		private fun invoke(arguments: Arguments, environment: Environment) {
			val params = arguments.params
			val fileToOpen = if (params.size == 1) (params.first() as? String)?.let { Path.of(it) } else null
			if (fileToOpen != null) {
				showMainFrame(WorkingMode.file, arguments.options, environment, fileToOpen)
			}
			else {
				showMainFrame(WorkingMode.open, arguments.options, environment, null)
			}
		}

		private fun showMainFrame(workingMode: WorkingMode, options: Options, environment: Environment, fileToOpen: Path?) {
			createLogger().info("show main frame: workingMode=$workingMode;file=$fileToOpen;options=$options")

			Ui.runInEdt {
				environment.frameManager.getFrame()?.let {
					it.toFront()
					if (workingMode != WorkingMode.open) {
						it.checkClose {
							if (fileToOpen != null || workingMode == WorkingMode.clipboard) {
								setMainFrameContent(workingMode, options, fileToOpen, it, environment)
							}
						}
					}
				} ?: run {
					val frame = MainFrame(workingMode, environment)
					frame.show()

					UpdateCheck(environment.buildInfo, environment.configurationProvider, environment.coroutineDispatcher, environment.paths, frame.dialogDisplayer).invoke()
					setMainFrameContent(workingMode, options, fileToOpen, frame, environment)
				}
			}
		}

		private fun setMainFrameContent(workingMode: WorkingMode, options: Options, fileToOpen: Path?, frame: MainFrame, environment: Environment) {
			if (fileToOpen != null) {
				frame.openFile(fileToOpen)
			}
			else {
				frame.setText(getContentFromClipboard(environment.paths), workingMode)
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

		private fun checkSystemTrayEnabled(configurationProvider: ConfigurationProviderImpl): Boolean {
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
				showMainFrame(WorkingMode.open, Options.NONE, environment, null)
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
						showMainFrame(WorkingMode.open, Options.NONE, environment, null)
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
				showMainFrame(WorkingMode.clipboard, Options.NONE, environment, null)
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

	private class ConfigurationProviderImpl(private val settingsRoot: Path, private val dialogDisplayer: DialogDisplayer) : ConfigurationProvider {
		private val classToConfiguration = HashMap<Class<ConfigurationFactory<Any>>, ConfigurationFile<Any>>()
		private val classToListeners = HashMap<Any, ArrayList<Runnable>>()

		@Synchronized
		override fun <D> get(factory: ConfigurationFactory<D>): D {
			return getFile(factory).config
		}

		@Synchronized
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

		@Synchronized
		override fun <T : Any> listenTo(clazz: KClass<T>, runnable: Runnable) {
			classToListeners.computeIfAbsent(clazz) { ArrayList() }.add(runnable)
		}

		@Suppress("UNCHECKED_CAST")
		private fun <D> getFile(factory: ConfigurationFactory<D>): ConfigurationFile<D> {
			val javaClass: Class<ConfigurationFactory<Any>> = factory.javaClass as Class<ConfigurationFactory<Any>>
			return classToConfiguration.computeIfAbsent(javaClass) {
				val name = factory.name() + ".json"
				val path = settingsRoot.resolve(name)
				ConfigurationFile.load(path, factory.serializer(), factory.default()) {
					val backup = Files.createTempFile(path.parent, name, "")
					Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
					Dialogs.showError("Configuration", "A serialization error occurred while processing '$name'. The associated configuration will be restored to its defaults.\n\nA backup of the original file has been created at $backup", dialogDisplayer)
					true
				} as ConfigurationFile<Any>
			} as ConfigurationFile<D>
		}
	}

	private data class Options(val backgroundMode : Boolean) {
		companion object {
			val NONE: Options = Options(false)
		}
	}

	private class Arguments(args: List<String>) {
		val params: List<String>
		val options: Options

		init {
			val parser = OptionParser()
			val backgroundOption = parser.accepts("background")
			val optionsSet: OptionSet = parser.parse(*args.toTypedArray())
			params = optionsSet.nonOptionArguments().stream().map { o -> o.toString() }.toList()
			options = Options(optionsSet.has(backgroundOption))
		}
	}
}