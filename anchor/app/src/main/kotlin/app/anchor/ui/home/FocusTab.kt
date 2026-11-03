package app.anchor.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.anchor.anchor.AnchorActivity
import app.anchor.core.focus.FocusMode
import app.anchor.core.focus.FocusPhase
import app.anchor.core.focus.FocusSession
import app.anchor.core.model.AnchorConfig
import app.anchor.data.AppInfo
import app.anchor.graph
import app.anchor.ui.AppIcon
import app.anchor.ui.Format
import app.anchor.ui.InfoCard
import app.anchor.ui.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ModeChoice(val label: String) { COUNTDOWN("Countdown"), POMODORO("Pomodoro"), STOPWATCH("Stopwatch") }

private data class PomodoroPreset(val label: String, val mode: FocusMode.Pomodoro)

private val pomodoroPresets = listOf(
    PomodoroPreset("25 / 5 × 4", FocusMode.Pomodoro(25, 5, 4)),
    PomodoroPreset("50 / 10 × 3", FocusMode.Pomodoro(50, 10, 3)),
    PomodoroPreset("90 / 15 × 2", FocusMode.Pomodoro(90, 15, 2)),
)

/** FR-06 focus timer and Journey C (Strict sessions end early only with the anchor). */
@Composable
fun FocusTab(config: AnchorConfig, padding: PaddingValues) {
    val g = LocalContext.current.graph
    val state by g.guard.state.collectAsStateWithLifecycle()
    val session = state.focus
    if (session != null) RunningSession(session, padding) else SessionSetup(config, padding)
}

@Composable
private fun RunningSession(session: FocusSession, padding: PaddingValues) {
    val context = LocalContext.current
    val g = context.graph
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val snap = session.snapshot(now)
    var refused by remember { mutableStateOf(false) }

    TabColumn(padding) {
        item {
            InfoCard {
                Text(
                    when (snap.phase) {
                        FocusPhase.WORK -> if (session.strict) "Strict focus" else "Focus"
                        FocusPhase.BREAK -> "Break"
                        FocusPhase.DONE -> "Done"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    snap.phaseRemainingMs?.let(Format::clock) ?: Format.clock(snap.elapsedMs),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (session.mode is FocusMode.Pomodoro) {
                    Text("Cycle ${snap.cycle} of ${(session.mode as FocusMode.Pomodoro).cycles}")
                }
                Text("Focused so far: ${Format.duration(snap.focusedMs)}")
            }
        }
        item {
            Text(
                "Put the phone face down and get to work. Only the apps you allowed will open" +
                    if (session.strict) "; Settings and uninstall are locked until the session ends." else ".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (refused) {
            item {
                Text(
                    "Strict mode: walk to your anchor to end early.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        item {
            val early = session.wouldEndEarly(now)
            if (session.strict && early) {
                Button(onClick = { AnchorActivity.unlock(context) }, modifier = Modifier.fillMaxWidth()) { Text("End early with my anchor") }
            } else {
                OutlinedButton(
                    onClick = { refused = !g.guard.endFocus(viaAnchor = false) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (early) "End session early" else "Finish session") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SessionSetup(config: AnchorConfig, padding: PaddingValues) {
    val context = LocalContext.current
    val g = context.graph
    val scope = rememberCoroutineScope()
    var choice by rememberSaveable { mutableStateOf(ModeChoice.COUNTDOWN) }
    var minutes by rememberSaveable { mutableStateOf(50) }
    var preset by rememberSaveable { mutableStateOf(0) }
    var strict by rememberSaveable { mutableStateOf(false) }
    var pickApps by remember { mutableStateOf(false) }

    TabColumn(padding) {
        item { SectionTitle("Start a focus session") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ModeChoice.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = choice == m,
                        onClick = { choice = m },
                        shape = SegmentedButtonDefaults.itemShape(i, ModeChoice.entries.size),
                    ) { Text(m.label) }
                }
            }
        }
        item {
            when (choice) {
                ModeChoice.COUNTDOWN -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 90, 120).forEach { m ->
                        FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                    }
                }
                ModeChoice.POMODORO -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pomodoroPresets.forEachIndexed { i, p ->
                        FilterChip(selected = preset == i, onClick = { preset = i }, label = { Text(p.label) })
                    }
                }
                ModeChoice.STOPWATCH -> Text("Runs until you stop it. Stopwatch sessions can always be ended.")
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = strict,
                    enabled = config.anchorRegistered && choice != ModeChoice.STOPWATCH,
                    role = Role.Switch,
                    onValueChange = { strict = it },
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (config.anchorRegistered) "No early exit, no Settings, no uninstall — unless you walk to your anchor."
                        else "Register an NFC or QR anchor first (More → Anchor).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = strict && config.anchorRegistered, onCheckedChange = null, enabled = config.anchorRegistered && choice != ModeChoice.STOPWATCH)
            }
        }
        item {
            InfoCard {
                Text("Allowed during focus", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (config.focusWhitelist.isEmpty()) "Only calls and Anchor. Add study apps, notes or a dictionary."
                    else config.focusWhitelist.joinToString { g.apps.label(it) },
                )
                TextButton(onClick = { pickApps = true }) { Text("Choose apps") }
            }
        }
        item {
            Button(
                onClick = {
                    val mode = when (choice) {
                        ModeChoice.COUNTDOWN -> FocusMode.Countdown(minutes)
                        ModeChoice.POMODORO -> pomodoroPresets[preset].mode
                        ModeChoice.STOPWATCH -> FocusMode.Stopwatch
                    }
                    g.guard.startFocus(mode, strict = strict && config.anchorRegistered && mode != FocusMode.Stopwatch, allowed = config.focusWhitelist)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start focusing") }
        }
    }

    if (pickApps) {
        AppPickerDialog(
            title = "Allowed during focus",
            initial = config.focusWhitelist,
            onDismiss = { pickApps = false },
            onConfirm = { picked ->
                pickApps = false
                scope.launch { g.configRepo.update { it.copy(focusWhitelist = picked) } }
            },
        )
    }
}

/** Multi-select list of launcher apps. */
@Composable
fun AppPickerDialog(
    title: String,
    initial: Set<String>,
    exclude: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val g = LocalContext.current.graph
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    LaunchedEffect(Unit) { apps = g.apps.launcherApps().filter { it.packageName !in exclude } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val on = app.packageName in picked
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { if (on) picked -= app.packageName else picked += app.packageName }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        AppIcon(app.packageName, 28.dp)
                        Text(app.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked.toSet()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
