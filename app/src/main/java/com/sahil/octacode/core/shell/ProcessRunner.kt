package com.sahil.octacode.core.shell

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Runs one process to completion and reports exactly what it did.
 *
 * Lives on its own because two callers now need it and the details matter
 * more than the duplication: reading both pipes concurrently, or killing the
 * process on timeout and keeping what it printed, are easy to get subtly
 * wrong. A second copy would be the copy nobody tested.
 *
 * Nothing here interprets the command or the result. [command] is only the
 * label that comes back in [ShellResult] — the argv is what actually runs, so a
 * caller can run a process that is not a shell at all.
 */
object ProcessRunner {

    suspend fun run(
        command: String,
        argv: List<String>,
        directory: File,
        environment: Map<String, String>,
        timeoutMs: Long,
    ): ShellResult = withContext(Dispatchers.IO) {
        val binary = argv.firstOrNull().orEmpty()
        val builder = ProcessBuilder(argv).directory(directory)
        builder.environment().putAll(environment)

        val process = try {
            builder.start()
        } catch (e: Exception) {
            return@withContext ShellResult(
                command = command,
                binary = binary,
                exitCode = ShellResult.EXIT_SPAWN_FAILED,
                stdout = "",
                stderr = e.message?.takeIf { it.isNotBlank() }
                    ?: "could not start ${binary.substringAfterLast('/')}"
            )
        }

        // Read both streams concurrently: a command that fills its stdout pipe
        // while we wait on stderr would deadlock otherwise.
        val out = async { process.inputStream.bufferedReader().use { it.readText() } }
        val err = async { process.errorStream.bufferedReader().use { it.readText() } }

        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            // The killed process's readers return once the pipes close; keep
            // whatever it printed rather than discarding it.
            return@withContext ShellResult(
                command = command,
                binary = binary,
                exitCode = ShellResult.EXIT_TIMEOUT,
                stdout = runCatching { out.await() }.getOrDefault(""),
                stderr = runCatching { err.await() }.getOrDefault(""),
                timedOut = true
            )
        }

        ShellResult(
            command = command,
            binary = binary,
            exitCode = process.exitValue(),
            stdout = out.await(),
            stderr = err.await()
        )
    }
}
