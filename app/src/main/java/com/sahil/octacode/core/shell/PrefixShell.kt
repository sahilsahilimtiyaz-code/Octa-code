package com.sahil.octacode.core.shell

import com.sahil.octacode.core.runtime.RuntimeIndex
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Result of one command. Never synthesised: [exitCode] is the process's own
 * value, or one of the conventional failures below when no process ran.
 */
data class ShellResult(
    val command: String,
    /** Absolute path of the interpreter that ran, or "" when none did. */
    val binary: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    /** True when the command actually reached a process. */
    val executed: Boolean get() = binary.isNotEmpty()

    companion object {
        /** Shell convention: the interpreter itself could not be found. */
        const val EXIT_NOT_INSTALLED = 127

        /** Shell convention: found, but not startable. */
        const val EXIT_SPAWN_FAILED = 126

        /** GNU timeout convention. */
        const val EXIT_TIMEOUT = 124
    }
}

/**
 * R3: runs commands inside the prefix that R1/R2 downloaded and verified.
 *
 * The division of labour with [RuntimeIndex] is deliberate — the index
 * answers *where* a tool is, from paths this app unpacked itself, and this
 * class answers *how* to run what the index named. Nothing here will start a
 * binary the index did not resolve, so there is no way to reach a file that
 * arrived from anywhere other than a SHA-256-checked download.
 *
 * A missing prefix is a failure, not an empty success: [run] returns exit
 * code 127 with a message that says what is missing and where to fix it.
 *
 * Why this needs targetSdk 28: see the note in app/build.gradle. Android
 * refuses exec() on app-writable files for apps targeting API 29+, so this
 * class would always have failed to start a process at all.
 */
class PrefixShell(
    private val prefix: File,
    private val home: File,
    private val index: RuntimeIndex
) {
    /** Interpreters in preference order; bash is what a Termux base ships. */
    private val candidates = listOf("bash", "dash", "sh")

    /** Absolute path of the shell to use, or null when nothing is unpacked. */
    fun shellPath(): File? = candidates.firstNotNullOfOrNull { index.commandPath(it) }

    /** Name of that shell for display ("bash"), or null. */
    fun shellName(): String? = shellPath()?.name

    /** What the terminal should tell the user before the first command. */
    fun isInstalled(): Boolean = shellPath() != null

    /**
     * Environment a Termux-built prefix expects.
     *
     * LD_LIBRARY_PATH is not optional: these packages are built against
     * libraries inside the prefix rather than the system's, so a shell
     * started without it fails to resolve its own dependencies. TERM is
     * "dumb" because there is no pseudo-terminal behind this runner —
     * claiming a tty that does not exist would make programs read input
     * that never arrives.
     */
    fun environment(): Map<String, String> {
        val bin = File(prefix, "bin").absolutePath
        val sbin = File(prefix, "sbin").absolutePath
        // The system PATH stays last as a fallback so `ls` still resolves if
        // the prefix is somehow incomplete — but it is never reached first,
        // because these packages must come from the prefix we verified.
        val inherited = System.getenv("PATH").orEmpty()
        val path = (listOf(bin, sbin) + inherited.split(':').filter { it.isNotBlank() })
            .joinToString(":")
        return linkedMapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to home.absolutePath,
            "TMPDIR" to File(home, "tmp").absolutePath,
            "PATH" to path,
            "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            "TERM" to "dumb",
            "LANG" to "en_US.UTF-8"
        )
    }

    /**
     * Run [command] through the resolved shell, blocking until it exits.
     *
     * Reading both streams concurrently matters: a command that fills its
     * stdout pipe while we wait on stderr would deadlock otherwise.
     */
    suspend fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult =
        withContext(Dispatchers.IO) {
            val shell = shellPath()
                ?: return@withContext ShellResult(
                    command = command,
                    binary = "",
                    exitCode = ShellResult.EXIT_NOT_INSTALLED,
                    stdout = "",
                    stderr = NOT_INSTALLED_MESSAGE
                )

            home.mkdirs()
            File(home, "tmp").mkdirs()

            val builder = ProcessBuilder(listOf(shell.absolutePath, "-c", command))
                .directory(home)
            builder.environment().putAll(environment())

            val process = try {
                builder.start()
            } catch (e: Exception) {
                return@withContext ShellResult(
                    command = command,
                    binary = shell.absolutePath,
                    exitCode = ShellResult.EXIT_SPAWN_FAILED,
                    stdout = "",
                    stderr = e.message?.takeIf { it.isNotBlank() }
                        ?: "could not start ${shell.name}"
                )
            }

            val out = async { process.inputStream.bufferedReader().use { it.readText() } }
            val err = async { process.errorStream.bufferedReader().use { it.readText() } }

            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                // The killed process's readers return once the pipes close;
                // keep whatever it printed rather than discarding it.
                return@withContext ShellResult(
                    command = command,
                    binary = shell.absolutePath,
                    exitCode = ShellResult.EXIT_TIMEOUT,
                    stdout = runCatching { out.await() }.getOrDefault(""),
                    stderr = runCatching { err.await() }.getOrDefault(""),
                    timedOut = true
                )
            }

            ShellResult(
                command = command,
                binary = shell.absolutePath,
                exitCode = process.exitValue(),
                stdout = out.await(),
                stderr = err.await()
            )
        }

    companion object {
        /** Long enough for a real build, short enough to never hang the UI. */
        const val DEFAULT_TIMEOUT_MS = 120_000L

        /** Wording the terminal shows verbatim — it names the fix. */
        const val NOT_INSTALLED_MESSAGE =
            "Runtime not installed. Open Settings > Runtime and install " +
                "\"Linux userland\", then run this again."
    }
}
