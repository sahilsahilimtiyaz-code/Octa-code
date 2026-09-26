package com.sahil.octacode.core.shell

import com.sahil.octacode.core.runtime.RuntimeIndex
import java.io.File

/**
 * Runs a command inside the glibc root filesystem, under PRoot.
 *
 * Why this class has to exist: the runtime R1/R2 installs is a **bionic**
 * userland. Termux builds every package against Android's libc, so every binary
 * in the prefix is bound to it. A prebuilt Linux tool — anything shipped as a
 * glibc or musl static binary, which is how tools like OpenCode are published
 * — needs `ld-linux-aarch64.so.1` and glibc, and no combination of Termux
 * packages supplies either. That is not a missing package; it is a different C
 * library.
 *
 * PRoot supplies the missing half without root. It is itself a bionic binary
 * that translates the syscalls a `chroot` would have needed, so a bionic
 * process can host a glibc filesystem underneath it. That is the whole trick,
 * and it is why `proot` was pinned in the manifest from R1 even though nothing
 * used it yet.
 *
 * What this deliberately does NOT do: put the Termux prefix on the guest PATH.
 * Those binaries are bionic, and running one inside a glibc root fails at
 * `ELF loader` — the interpreter path resolves against the guest and is not
 * there. A tool on PATH that dies on every invocation is worse than one that
 * is absent, because the error names a file that exists.
 */
class ProotRunner(
    private val rootfs: File,
    private val prefix: File,
    private val home: File,
    private val index: RuntimeIndex,
) {

    /** PRoot is a Termux package, so the index is the only way to find it. */
    fun prootPath(): File? = index.commandPath(PROOT)

    /**
     * True only when both halves are present: the executor this app verified,
     * and a root filesystem with a shell in it. A PRoot with no rootfs starts
     * and then fails on every command, which would read as "installed" to any
     * check that only asked about the binary.
     */
    fun isInstalled(): Boolean =
        prootPath() != null && File(rootfs, GUEST_SHELL).isFile

    /**
     * Host-side environment.
     *
     * LD_LIBRARY_PATH is not optional for the same reason it is not optional in
     * [PrefixShell.environment]: these packages resolve their own dependencies
     * inside the prefix rather than from the system.
     *
     * PATH puts the prefix's bin first so `proot` itself resolves. That is the
     * host side only — the guest gets a clean environment from `env -i` below,
     * which is the boundary that matters.
     */
    fun environment(): Map<String, String> {
        val bin = File(prefix, "bin").absolutePath
        val sbin = File(prefix, "sbin").absolutePath
        val inherited = System.getenv("PATH").orEmpty()
        return linkedMapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to home.absolutePath,
            "TMPDIR" to File(home, "tmp").absolutePath,
            "PATH" to (listOf(bin, sbin) + inherited.split(':').filter { it.isNotBlank() })
                .joinToString(":"),
            "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            "TERM" to "dumb"
        )
    }

    /**
     * Runs [command] through the guest's bash.
     *
     * Reports like any other shell call: a missing runtime is exit 127 with a
     * sentence naming the fix, never an empty success.
     */
    suspend fun run(
        command: String,
        binds: List<File> = emptyList(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): ShellResult {
        val proot = prootPath()
            ?: return ShellResult(
                command = command,
                binary = "",
                exitCode = ShellResult.EXIT_NOT_INSTALLED,
                stdout = "",
                stderr = PROOT_MISSING_MESSAGE
            )
        if (!File(rootfs, GUEST_SHELL).isFile) {
            return ShellResult(
                command = command,
                binary = proot.absolutePath,
                exitCode = ShellResult.EXIT_NOT_INSTALLED,
                stdout = "",
                stderr = ROOTFS_MISSING_MESSAGE
            )
        }

        home.mkdirs()
        return ProcessRunner.run(
            command = command,
            argv = prootArgv(
                proot = proot.absolutePath,
                rootfs = rootfs.absolutePath,
                home = home.absolutePath,
                command = command,
                binds = binds.map { it.absolutePath }
            ),
            directory = home,
            environment = environment(),
            timeoutMs = timeoutMs
        )
    }

    companion object {
        const val PROOT = "proot"
        const val GUEST_HOME = "/root"
        const val GUEST_SHELL = "/bin/bash"
        const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

        /**
         * The environment the guest command sees.
         *
         * PATH is Ubuntu's own — not Android's, and deliberately not the
         * Termux prefix's. Built explicitly rather than inherited so a `bash`
         * started here behaves the way the same `bash` would on a real Ubuntu
         * box, instead of resolving half its tools to bionic binaries that
         * cannot load under a glibc root.
         */
        fun guestEnvironment(): Map<String, String> = linkedMapOf(
            "HOME" to GUEST_HOME,
            "PATH" to GUEST_PATH,
            "TMPDIR" to "/tmp",
            "TERM" to "xterm",
            "LANG" to "C.UTF-8"
        )

        /** Generous next to a real build, short enough that the UI never hangs. */
        const val DEFAULT_TIMEOUT_MS = 120_000L

        const val PROOT_MISSING_MESSAGE =
            "PRoot is not installed. Open Settings > Runtime and install " +
                "\"PRoot sandbox\", then run this again."

        const val ROOTFS_MISSING_MESSAGE =
            "The glibc userland is not installed. Open Settings > Runtime and " +
                "install \"glibc userland\", then run this again."
    }
}

/**
 * The exact PRoot invocation, as a pure function of its inputs.
 *
 * Kept separate from [ProotRunner.run] so the argument list can be pinned in a
 * test without a root filesystem, a PRoot binary, or a device. This is the part
 * that is easy to get quietly wrong: a missing `-b /proc` still starts, a
 * missing `-0` still starts, and a Termux prefix left on the guest PATH starts
 * too — and then every bionic binary on it dies on the ELF loader.
 */
internal fun prootArgv(
    proot: String,
    rootfs: String,
    home: String,
    command: String,
    binds: List<String> = emptyList(),
    guestEnv: Map<String, String> = ProotRunner.guestEnvironment(),
): List<String> = buildList {
    add(proot)
    // Root first: everything after it is interpreted relative to this.
    add("-r")
    add(rootfs)
    // The kernel interfaces a Linux process expects to find. PRoot does not
    // synthesise these, and without /proc a great deal of userspace gives up.
    add("-b")
    add("/dev")
    add("-b")
    add("/proc")
    add("-b")
    add("/sys")
    // The guest's home is the app's own home, so a file written inside the
    // sandbox is a file the app can still see and the user can still reach.
    add("-b")
    add("$home:${ProotRunner.GUEST_HOME}")
    for (bind in binds) {
        add("-b")
        add(bind)
    }
    add("-w")
    add(ProotRunner.GUEST_HOME)
    // Present the caller as root inside. Package tools refuse to run otherwise,
    // and nothing here is actually privileged.
    add("-0")
    // Do not leave the guest's process tree running after this one exits.
    add("--kill-on-exit")
    // -i because the environment must be the one below, not the one Android
    // handed this process.
    add("/usr/bin/env")
    add("-i")
    guestEnv.forEach { (key, value) -> add("$key=$value") }
    add(ProotRunner.GUEST_SHELL)
    add("-c")
    add(command)
}
