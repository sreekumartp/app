package app.anchor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.anchor.core.exam.DailyExamTarget
import app.anchor.core.exam.ExamDayKind
import app.anchor.core.exam.ExamPlanner
import app.anchor.core.model.AnchorConfig
import app.anchor.core.model.ExamPlan
import app.anchor.core.model.ExamType
import app.anchor.graph
import app.anchor.ui.Format
import app.anchor.ui.InfoCard
import app.anchor.ui.SectionTitle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** IN-06 exam-aware intensity. */
@Composable
fun ExamTab(config: AnchorConfig, padding: PaddingValues) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val exam = config.exam
    if (exam == null || editing) {
        ExamForm(exam, padding, onCancel = if (exam != null) ({ editing = false }) else null) { plan ->
            editing = false
            scope.launch { g.configRepo.update { it.copy(exam = plan) } }
        }
    } else {
        ExamOverview(config, exam, padding, onEdit = { editing = true }, onRemove = {
            scope.launch { g.configRepo.update { it.copy(exam = null) } }
        }, onMocks = { mocks ->
            scope.launch { g.configRepo.update { c -> c.copy(exam = c.exam?.copy(mockTestEpochDays = mocks)) } }
        })
    }
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun epochDayLabel(d: Long) = LocalDate.ofEpochDay(d).format(dateFmt)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamOverview(
    config: AnchorConfig,
    exam: ExamPlan,
    padding: PaddingValues,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMocks: (Set<Long>) -> Unit,
) {
    val g = LocalContext.current.graph
    val today = LocalDate.now().toEpochDay()
    val target = ExamPlanner.targetFor(exam, today)
    val days = rememberWeekUsage()
    val usedLeisure = (days[LocalDate.now()]?.totalMs(config.taggedPackages) ?: 0) / 60_000
    val focusToday = remember(days) { g.secure.stats()[today]?.focusMinutes ?: 0 }
    var pickMock by remember { mutableStateOf(false) }

    TabColumn(padding) {
        item {
            InfoCard {
                Text(exam.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (target.kind) {
                        ExamDayKind.EXAM_DAY -> "Exam day. You've got this."
                        ExamDayKind.AFTER_EXAM -> "The exam is behind you. Set a new one or remove this plan."
                        else -> "${target.daysLeft} days to go · ${epochDayLabel(exam.examEpochDay)}"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(kindLabel(target))
            }
        }
        if (target.kind != ExamDayKind.AFTER_EXAM && target.kind != ExamDayKind.EXAM_DAY) {
            item {
                InfoCard {
                    Text("Today's focus target", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { (focusToday.toFloat() / target.focusTargetMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${Format.minutes(focusToday.toLong())} of ${Format.minutes(target.focusTargetMinutes.toLong())}")
                    Text("Leisure allowance", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(
                        progress = { (usedLeisure.toFloat() / target.leisureAllowanceMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (usedLeisure >= target.leisureAllowanceMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    )
                    Text("${Format.minutes(usedLeisure)} used of ${Format.minutes(target.leisureAllowanceMinutes.toLong())} across tagged apps")
                }
            }
        }
        item { SectionTitle("Mock tests") }
        item {
            Text(
                "Mock-test days get a full lockdown from ${Format.minuteOfDay(exam.mockStartMinute)} to ${Format.minuteOfDay(exam.mockEndMinute)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                exam.mockTestEpochDays.sorted().forEach { d ->
                    InputChip(selected = false, onClick = { onMocks(exam.mockTestEpochDays - d) }, label = { Text(epochDayLabel(d) + "  ✕") })
                }
                TextButton(onClick = { pickMock = true }) { Text("Add mock test") }
            }
        }
        item { SectionTitle("Next two weeks") }
        items(ExamPlanner.outlook(exam, today, 14), key = { it.epochDay }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(epochDayLabel(t.epochDay), Modifier.weight(1f))
                Text(
                    when (t.kind) {
                        ExamDayKind.MOCK_TEST -> "Mock · focus ${Format.minutes(t.focusTargetMinutes.toLong())}"
                        ExamDayKind.EXAM_DAY -> "Exam"
                        ExamDayKind.TAPER -> "Taper · leisure ${Format.minutes(t.leisureAllowanceMinutes.toLong())}"
                        else -> "Focus ${Format.minutes(t.focusTargetMinutes.toLong())} · leisure ${Format.minutes(t.leisureAllowanceMinutes.toLong())}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit plan") }
                TextButton(onClick = onRemove) { Text("Remove plan") }
            }
        }
    }

    if (pickMock) {
        DateDialog(minEpochDay = today, maxEpochDay = exam.examEpochDay - 1, onDismiss = { pickMock = false }) { d ->
            pickMock = false
            onMocks(exam.mockTestEpochDays + d)
        }
    }
}

private fun kindLabel(t: DailyExamTarget) = when (t.kind) {
    ExamDayKind.RAMP -> "Targets rise and leisure tightens as the date nears."
    ExamDayKind.TAPER -> "Taper: lighter revision days to arrive rested."
    ExamDayKind.MOCK_TEST -> "Mock-test day: lockdown ${Format.minuteOfDay(t.lockdownStartMinute ?: 0)}–${Format.minuteOfDay(t.lockdownEndMinute ?: 0)}."
    ExamDayKind.EXAM_DAY -> "No targets today."
    ExamDayKind.AFTER_EXAM -> ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamForm(existing: ExamPlan?, padding: PaddingValues, onCancel: (() -> Unit)?, onSave: (ExamPlan) -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ExamType.CLAT) }
    var custom by remember { mutableStateOf(existing?.customName ?: "") }
    var date by remember { mutableStateOf(existing?.examEpochDay) }
    var focus by remember { mutableFloatStateOf((existing?.baseFocusMinutes ?: 240).toFloat()) }
    var leisure by remember { mutableFloatStateOf((existing?.baseLeisureMinutes ?: 90).toFloat()) }
    var pickDate by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()

    TabColumn(padding) {
        item { SectionTitle("Plan backward from your exam") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExamType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
            }
        }
        if (type == ExamType.CUSTOM) {
            item { OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Exam name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        }
        item {
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(date?.let { "Exam date: ${epochDayLabel(it)}" } ?: "Pick the exam date")
            }
        }
        item {
            Column {
                Text("Daily focus target to start with: ${Format.minutes(focus.roundToInt().toLong())}")
                Slider(value = focus, onValueChange = { focus = it }, valueRange = 60f..480f, steps = 13)
                Text("Daily leisure allowance to start with: ${Format.minutes(leisure.roundToInt().toLong())}")
                Slider(value = leisure, onValueChange = { leisure = it }, valueRange = 30f..180f, steps = 9)
                Text(
                    "Over the last ${ExamPlanner.RAMP_DAYS} days the focus target rises up to 1.5× and leisure tightens to a third, with a lighter taper in the final ${ExamPlanner.TAPER_DAYS} days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = date != null && (type != ExamType.CUSTOM || custom.isNotBlank()),
                    onClick = {
                        val base = existing ?: ExamPlan(type, examEpochDay = date!!)
                        onSave(
                            base.copy(
                                type = type,
                                customName = custom.trim().ifBlank { null },
                                examEpochDay = date!!,
                                baseFocusMinutes = focus.roundToInt(),
                                baseLeisureMinutes = leisure.roundToInt(),
                                mockTestEpochDays = base.mockTestEpochDays.filter { it < date!! }.toSet(),
                            ),
                        )
                    },
                ) { Text("Save plan") }
                onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
            }
        }
    }

    if (pickDate) {
        DateDialog(minEpochDay = today + 1, maxEpochDay = today + 3 * 365, onDismiss = { pickDate = false }) {
            date = it
            pickDate = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(minEpochDay: Long, maxEpochDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                return d in minEpochDay..maxEpochDay
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
