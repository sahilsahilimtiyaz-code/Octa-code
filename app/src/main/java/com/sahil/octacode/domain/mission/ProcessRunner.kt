package com.sahil.octacode.domain.mission

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M3c process boundary for test/build/install. No fake success:
 * missing tools → which()=null; non-zero exit → ProcessResult.
 */
interface ProcessRunner {
    suspend fun which(command: String): String?

    suspend fun run(
        command: List<String>,
        workingDir: File,
        timeoutMs: Long = 180_000L
    ): ProcessResult

    companion object {
        /** Default: tools unavailable (unit tests, no shell on device yet). */
        val Unavailable: ProcessRunner = object : ProcessRunner {
            override suspend fun which(command: String): String? = null
            override suspend fun run(
                command: List<String>,
                workingDir: File,
                timeoutMs: Long
            ): ProcessResult = ProcessResult(
                exitCode = -1,
                stdout = "",
                stderr = "process runner unavailable",
                timedOut = false
            )
        }
    }
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0

    fun tail(maxChars: Int = 1200): String {
        val combined = (stdout + "\n" + stderr).trim()
        return if (combined.length <= maxChars) combined else "…" + combined.takeLast(maxChars)
    }
}

/** Host/device JVM process runner — PATH lookup + ProcessBuilder with timeout. */
class JvmProcessRunner : ProcessRunner {

    override suspend fun which(command: String): String? = withContext(Dispatchers.IO) {
        val path = System.getenv("PATH") ?: return@withContext null
        val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        path.split(File.pathSeparator).forEach { dir ->
            if (dir.isBlank()) return@forEach
            val base = File(dir, command)
            if (base.isFile && (base.canExecute() || !windows)) {
                return@withContext base.absolutePath
            }
            if (windows) {
                listOf(".exe", ".bat", ".cmd").forEach { ext ->
                    val f = File(dir, command + ext)
                    if (f.isFile) return@withContext f.absolutePath
                }
            }
        }
        null
    }

    override suspend fun run(
        command: List<String>,
        workingDir: File,
        timeoutMs: Long
    ): ProcessResult = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(false)
            val process = pb.start()
            val stdoutBox = StringBuilder()
            val stderrBox = StringBuilder()
            val outCapture = Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(stdoutBox) { stdoutBox.appendLine(line) }
                }
            }
            val errCapture = Thread {
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(stderrBox) { stderrBox.appendLine(line) }
                }
            }
            outCapture.isDaemon = true
            errCapture.isDaemon = true
            outCapture.start()
            errCapture.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                outCapture.join(500)
                errCapture.join(500)
                return@withContext ProcessResult(
                    exitCode = -1,
                    stdout = synchronized(stdoutBox) { stdoutBox.toString() },
                    stderr = synchronized(stderrBox) {
                        stderrBox.toString() + "\ntimed out after ${timeoutMs}ms"
                    },
                    timedOut = true
                )
            }
            outCapture.join(1000)
            errCapture.join(1000)
            ProcessResult(
                exitCode = process.exitValue(),
                stdout = synchronized(stdoutBox) { stdoutBox.toString() },
                stderr = synchronized(stderrBox) { stderrBox.toString() },
                timedOut = false
            )
        } catch (t: Throwable) {
            ProcessResult(
                exitCode = -1,
                stdout = "",
                stderr = t.message ?: t::class.java.simpleName,
                timedOut = false
            )
        }
    }
}
