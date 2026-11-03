package app.anchor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.anchor.core.model.AnchorConfig
import app.anchor.core.model.Defaults
import app.anchor.core.model.InterventionSettings
import app.anchor.core.model.Schedule
import app.anchor.core.model.Surface
import app.anchor.core.model.TaggedApp
import app.anchor.graph
import app.anchor.ui.AppIcon
import app.anchor.ui.Format
import app.anchor.ui.InfoCard
import app.anchor.ui.SectionTitle
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private val limitOptions = listOf<Int?>(null, 15, 30, 45, 60, 90, 120)

/** FR-02 limits, FR-03 schedules, FR-05 surfaces and intervention settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesTab(config: AnchorConfig, padding: PaddingValues) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var addApps by remember { mutableStateOf(false) }
    var editSchedule by remember { mutableStateOf<Schedule?>(null) }

    fun update(f: (AnchorConfig) -> AnchorConfig) { scope.launch { g.configRepo.update(f) } }
    fun updateApp(pkg: String, f: (TaggedApp) -> TaggedApp) =
        update { c -> c.copy(apps = c.apps.map { if (it.packageName == pkg) f(it) else it }) }

    TabColumn(padding) {
        item { SectionTitle("Tagged apps") }
        items(config.apps, key = { it.packageName }) { app ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app.packageName)
                    Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { update { c -> c.copy(apps = c.apps.filterNot { it.packageName == app.packageName }) } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove ${app.label}")
                    }
                }
                Text("Daily limit", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    limitOptions.forEach { opt ->
                        FilterChip(
                            selected = app.dailyLimitMinutes == opt,
                            onClick = { updateApp(app.packageName) { it.copy(dailyLimitMinutes = opt) } },
                            label = { Text(opt?.let { Format.minutes(it.toLong()) } ?: "None") },
                        )
                    }
                }
                val surfaces = Surface.forPackage(app.packageName)
                if (surfaces.isNotEmpty()) {
                    Text("Block inside the app", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        surfaces.forEach { s ->
                            val on = s in app.blockedSurfaces
                            FilterChip(
                                selected = on,
                                onClick = { updateApp(app.packageName) { it.copy(blockedSurfaces = if (on) it.blockedSurfaces - s else it.blockedSurfaces + s) } },
                                label = { Text(s.label) },
                            )
                        }
                    }
                }
                ToggleRow("Pause habitual opens", "A breathing gate or intent prompt when an open looks habitual", app.frictionEnabled) { on ->
                    updateApp(app.packageName) { it.copy(frictionEnabled = on) }
                }
            }
        }
        item { OutlinedButton(onClick = { addApps = true }, modifier = Modifier.fillMaxWidth()) { Text("Tag more apps") } }

        item { SectionTitle("Schedules") }
        items(config.schedules, key = { it.id }) { s ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text("${Format.minuteOfDay(s.startMinute)}–${Format.minuteOfDay(s.endMinute)} · ${daysLabel(s.days)}")
                        Text(
                            if (s.packages.isEmpty()) "All tagged apps" else s.packages.joinToString { g.apps.label(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.enabled,
                        onCheckedChange = { on -> update { c -> c.copy(schedules = c.schedules.map { if (it.id == s.id) it.copy(enabled = on) else it }) } },
                    )
                }
                Row {
                    TextButton(onClick = { editSchedule = s }) { Text("Edit") }
                    TextButton(onClick = { update { c -> c.copy(schedules = c.schedules.filterNot { it.id == s.id }) } }) { Text("Delete") }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { editSchedule = Schedule(UUID.randomUUID().toString(), "New schedule", days = (1..5).toSet(), startMinute = 21 * 60, endMinute = 7 * 60) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add a schedule") }
        }

        item { SectionTitle("Interventions") }
        item { InterventionsCard(config.interventions) { f -> update { it.copy(interventions = f(it.interventions)) } } }
    }

    if (addApps) {
        AppPickerDialog(
            title = "Tag apps",
            initial = emptySet(),
            exclude = config.taggedPackages,
            onDismiss = { addApps = false },
            onConfirm = { picked ->
                addApps = false
                update { c -> c.copy(apps = c.apps + picked.map { Defaults.tagged(it, g.apps.label(it), c.goal) }) }
            },
        )
    }

    editSchedule?.let { s ->
        ScheduleDialog(
            initial = s,
            onDismiss = { editSchedule = null },
            onSave = { saved ->
                editSchedule = null
                update { c ->
                    val exists = c.schedules.any { it.id == saved.id }
                    c.copy(schedules = if (exists) c.schedules.map { if (it.id == saved.id) saved else it } else c.schedules + saved)
                }
            },
        )
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterventionsCard(s: InterventionSettings, update: ((InterventionSettings) -> InterventionSettings) -> Unit) {
    InfoCard {
        ToggleRow("Habitual-open prediction", "Learns on this phone which opens are habits; nothing leaves the device", s.predictionEnabled) { on ->
            update { it.copy(predictionEnabled = on) }
        }
        ToggleRow("Doomscroll fuse", "Dims the screen and starts a 5-minute fuse after ${s.doomscrollSwipes} rapid swipes, in any app", s.doomscrollEnabled) { on ->
            update { it.copy(doomscrollEnabled = on) }
        }
        if (s.doomscrollEnabled) {
            var swipes by remember(s.doomscrollSwipes) { mutableFloatStateOf(s.doomscrollSwipes.toFloat()) }
            Text("Rapid swipes before the fuse: ${swipes.roundToInt()}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = swipes,
                onValueChange = { swipes = it },
                onValueChangeFinished = { update { it.copy(doomscrollSwipes = swipes.roundToInt()) } },
                valueRange = 8f..40f,
                steps = 31,
            )
        }
        Text("\"Just browsing\" fuse", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(5, 10, 15, 20).forEach { m ->
                FilterChip(selected = s.browsingFuseMinutes == m, onClick = { update { it.copy(browsingFuseMinutes = m) } }, label = { Text("$m min") })
            }
        }
        Text("Breathing pause", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 4, 5).forEach { sec ->
                FilterChip(selected = s.breathingSeconds == sec, onClick = { update { it.copy(breathingSeconds = sec) } }, label = { Text("$sec s") })
            }
        }
    }
}

private fun daysLabel(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Every day"
    (1..5).toSet() -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(initial: Schedule, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    val days = remember { mutableStateListOf<Int>().apply { addAll(initial.days.sorted()) } }
    val start = rememberTimePickerState(initial.startMinute / 60, initial.startMinute % 60, is24Hour = true)
    val end = rememberTimePickerState(initial.endMinute / 60 % 24, initial.endMinute % 60, is24Hour = true)
    var editingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name == "New schedule") "New schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d.value in days
                        FilterChip(
                            selected = on,
                            onClick = { if (on) days -= d.value else days += d.value },
                            label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !editingEnd, onClick = { editingEnd = false }, label = { Text("Starts ${"%02d:%02d".format(start.hour, start.minute)}") })
                    FilterChip(selected = editingEnd, onClick = { editingEnd = true }, label = { Text("Ends ${"%02d:%02d".format(end.hour, end.minute)}") })
                }
                TimeInput(state = if (editingEnd) end else start)
                val s = start.hour * 60 + start.minute
                val e = end.hour * 60 + end.minute
                if (e <= s) Text("Runs overnight into the next morning.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty() && name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            days = days.toSet(),
                            startMinute = start.hour * 60 + start.minute,
                            endMinute = end.hour * 60 + end.minute,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
