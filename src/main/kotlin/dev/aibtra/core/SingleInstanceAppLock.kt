/*
 * Copyright 2023 https://github.com/aibtra/aibtra. Use of this source code is governed by the GNU General Public License v3.0.
 */

package dev.aibtra.core

import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path

class SingleInstanceAppLock<T>(
	private val appPath: Path,
	val initialize: () -> T?,
	val runCommand: (command: String, state: T) -> Unit
) {
	fun startup(startupCommand: String) {
		val loopbackAddress = InetAddress.getLoopbackAddress()
		val lockFile = appPath.resolve("lock")
		val portFile = appPath.resolve("lock.port")
		if (tryConnectToExistingInstance(lockFile, portFile, loopbackAddress, startupCommand)) {
			return
		}

		try {
			ServerSocket(0).use { server ->
				val port = server.localPort
				LOG.info("Socket started at port $port")

				val raFile = RandomAccessFile(lockFile.toFile(), "rw")
				val fileLock = try {
					raFile.channel.tryLock()
				} catch (e: Exception) {
					raFile.close()
					throw e
				}

				if (fileLock == null) {
					// This might be the result of a concurrent start-up of another instance, hence give it a second try.
					if (tryConnectToExistingInstance(lockFile, portFile, loopbackAddress, startupCommand)) {
						return
					}

					LOG.error("Failed to acquire lock on $lockFile")
				}

				Runtime.getRuntime().addShutdownHook(object : Thread() {
					override fun run() {
						try {
							server.close()
							fileLock.release()
							raFile.close()
							Files.deleteIfExists(portFile)
							Files.deleteIfExists(lockFile)

							LOG.info("Socket shutdown")
						} catch (e: Exception) {
							LOG.error("Failed to shutdown instance: ${e.message}", e)
						}
					}
				})

				Files.write(portFile, port.toString().toByteArray())

				val state = initialize() ?: return
				val acceptThread = Thread {
					while (true) {
						try {
							server.accept().use {
								runCommand(String(it.getInputStream().readAllBytes()), state)
							}
						} catch (e: Exception) {
							if (server.isClosed) {
								break
							}

							LOG.error("Failed to accept request: ${e.message}", e)
						}
					}
				}
				acceptThread.isDaemon = true
				acceptThread.start()

				while (true) {
					Thread.sleep(Long.MAX_VALUE)
				}
			}
		} catch (e: Exception) {
			LOG.error("Failed to start up socket: ${e.message}", e)
		}
	}

	private fun tryConnectToExistingInstance(lockFile: Path, portFile: Path, loopbackAddress: InetAddress, startupCommand: String): Boolean {
		if (!Files.exists(lockFile)) {
			return false
		}

		try {
			// Try to delete stale lock files.
			if (Files.deleteIfExists(lockFile)) {
				return false
			}
		} catch (_: Exception) {
		}

		// The other instance may just be starting up. Let's give it some time to create the port file after having created the lock file.
		var existsChecks = 0
		while (existsChecks < 10 && !Files.exists(portFile)) {
			Thread.sleep(100)
			existsChecks++
		}

		try {
			val port = String(Files.readAllBytes(portFile)).toInt()
			val socket = Socket(loopbackAddress, port)

			LOG.info("Connected to running instance, sending '${startupCommand}'")

			val outputStream = socket.getOutputStream()
			outputStream.write(startupCommand.toByteArray())
			socket.shutdownOutput()
			try {
				socket.getInputStream().read()
			} catch (e: Exception) {
				LOG.error("Failed waiting for ack of running instance: ${e.message}", e)
			}
			System.exit(0)
		} catch (e: Exception) {
			LOG.error("Failed to connect to running instance: ${e.message}", e)
			Files.deleteIfExists(lockFile)
			return true
		}
		return false
	}

	companion object {
		private val LOG = Logger.getLogger(this::class)
	}
}