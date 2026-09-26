package com.sahil.octacode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sahil.octacode.core.shell.PrefixShell
import com.sahil.octacode.core.shell.ProotRunner
import com.sahil.octacode.core.shell.ShellResult
import com.sahil.octacode.ui.components.StreamTerminal
import com.sahil.octacode.ui.theme.NeonGreen
import com.sahil.octacode.ui.theme.OnDarkMuted
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Which userland a command runs in.
 *
 * Two, because they are genuinely different systems rather than two skins:
 * the Termux prefix is bionic, the glibc root is not, and a binary built for
 * one will not run in the other. Offering both is honest; pretending they
 * were interchangeable is what produces "works on my phone".
 */
enum class ShellTarget(val title: String) {
    TERMUX("Termux"),
    GLIBC("glibc")
}

/**
 * A real shell, now that the runtime it runs inside can actually execute.
 *
 * The prefix was downloadable and verifiable from R1 onward, but starting a
 * process in it is only possible because the app targets API 28 — see the
 * note in app/build.gradle. Nothing here fakes a result: every command is
 * echoed, its output appended verbatim, and a non-zero exit is reported as
 * one. A runtime that has not been installed says so and offers the screen
 * that installs it, rather than showing a prompt that would accept input and
 * do nothing with it.
 *
 * Output ordering across the two streams is not preserved — stdout and stderr
 * are read concurrently to avoid pipe deadlock, so a command writing to both
 * will have its stderr collected after its stdout. The exit code is exact.
 */
/**
 * Which userland a route asked for.
 *
 * Unknown values fall back to Termux rather than being honoured: it is the one
 * that works without the optional glibc install, so a malformed argument
 * lands somewhere usable instead of on a screen that says "not installed".
 */
internal fun shellTargetFor(userland: String): ShellTarget =
    if (userland.equals("glibc", ignoreCase = true)) ShellTarget.GLIBC else ShellTarget.TERMUX

@Composable
fun TerminalScreen(
    onOpenRuntime: () -> Unit = {},
    initialUserland: String = ShellTarget.TERMUX.title,
    shell: PrefixShell = koinInject(),
    proot: ProotRunner = koinInject(),
) {
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    // Keyed on the argument, so arriving from an agent row re-points the
    // terminal at the userland that agent needs rather than keeping whatever
    // the user last looked at.
    var target by remember(initialUserland) {
        mutableStateOf(shellTargetFor(initialUserland))
    }
    val scope = rememberCoroutineScope()

    // Recomputed on every composition rather than remembered: installing the
    // runtime happens on another screen, and the state here must be true the
    // moment the user navigates back.
    val shellPath = shell.shellPath()
    val glibcReady = proot.isInstalled()

    // Readiness is a property of the *selected* userland, not of the terminal.
    // A glibc prefix that is not installed does not make the Termux one stop
    // working, so the check follows the selection.
    val installed = when (target) {
        ShellTarget.TERMUX -> shellPath != null
        ShellTarget.GLIBC -> glibcReady
    }
    val notInstalledMessage = when (target) {
        ShellTarget.TERMUX -> PrefixShell.NOT_INSTALLED_MESSAGE
        ShellTarget.GLIBC ->
            if (proot.prootPath() == null) ProotRunner.PROOT_MISSING_MESSAGE
            else ProotRunner.ROOTFS_MISSING_MESSAGE
    }

    fun submit() {
        val command = input.trim()
        if (command.isEmpty() || busy) return
        input = ""
        busy = true
        lines = lines + "$ $command"
        scope.launch {
            val result = when (target) {
                ShellTarget.TERMUX -> shell.run(command)
                ShellTarget.GLIBC -> proot.run(command)
            }
            lines = lines + renderShellOutput(result)
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (installed) {
                    when (target) {
                        ShellTarget.TERMUX -> "${shellPath?.name} · ready"
                        ShellTarget.GLIBC -> "glibc · ready"
                    }
                } else {
                    "Runtime not installed"
                },
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = if (installed) NeonGreen else OnDarkMuted
            )
            if (busy) {
                Text(
                    text = "running…",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = OnDarkMuted
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Both chips are always enabled. Hiding the glibc one until it is
        // installed would make the second userland undiscoverable, and the
        // message it carries is the install instruction.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShellTarget.entries.forEach { option ->
                val ready = when (option) {
                    ShellTarget.TERMUX -> shellPath != null
                    ShellTarget.GLIBC -> glibcReady
                }
                FilterChip(
                    selected = target == option,
                    onClick = { target = option },
                    // The trailing mark is the honest part: a userland you can
                    // pick but not yet run would be a row that silently does
                    // nothing, which is the one thing this app does not ship.
                    label = {
                        Text(
                            if (ready) option.title else "${option.title} · not installed",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        StreamTerminal(
            lines = lines,
            modifier = Modifier.weight(1f),
            emptyHint = if (installed) {
                "Ready. Type a command below and press Run."
            } else {
                notInstalledMessage
            },
            // Give the terminal the height the screen is not using for input.
            maxHeight = 4096.dp
        )

        Spacer(Modifier.height(10.dp))

        if (!installed) {
            Button(
                onClick = onOpenRuntime,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Runtime settings")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Command") },
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                Button(
                    onClick = { submit() },
                    enabled = !busy && input.isNotBlank()
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Run")
                }
            }
        }
    }
}

/**
 * Turn one [ShellResult] into display lines: real output first, then a single
 * line recording the exit status when it is not a clean zero.
 *
 * A shell that never started (127) is deliberately not given an exit line —
 * the message already states what is missing, and a bare "exit 127" would
 * imply a process ran when none did.
 */
internal fun renderShellOutput(result: ShellResult): List<String> = buildList {
    addAll(result.stdout.lines().dropLastWhile { it.isEmpty() })
    addAll(result.stderr.lines().dropLastWhile { it.isEmpty() })
    if (result.executed) {
        when {
            result.timedOut -> add("→ timed out")
            result.exitCode != 0 -> add("→ exit ${result.exitCode}")
        }
    }
}
