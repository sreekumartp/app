package app.anchor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.anchor.core.model.AnchorConfig
import app.anchor.core.usage.DayUsage
import app.anchor.core.usage.UsageAggregator
import app.anchor.data.DayStats
import app.anchor.data.Permissions
import app.anchor.graph
import app.anchor.ui.AppIcon
import app.anchor.ui.Format
import app.anchor.ui.InfoCard
import app.anchor.ui.SectionTitle
import app.anchor.ui.StatTile
import app.anchor.ui.rememberResumeCount
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Insights),
    FOCUS("Focus", Icons.Outlined.Timer),
    RULES("Rules", Icons.AutoMirrored.Outlined.Rule),
    EXAM("Exam", Icons.Outlined.CalendarMonth),
    MORE("More", Icons.Outlined.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(config: AnchorConfig) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == Tab.TODAY) "Anchor" else tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp)
        when (tab) {
            Tab.TODAY -> TodayTab(config, content)
            Tab.FOCUS -> FocusTab(config, content)
            Tab.RULES -> RulesTab(config, content)
            Tab.EXAM -> ExamTab(config, content)
            Tab.MORE -> MoreTab(config, content)
        }
    }
}

/** Shared lazy column for every tab. */
@Composable
fun TabColumn(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** Usage for the last 7 days, refreshed on resume and every minute. */
@Composable
fun rememberWeekUsage(): Map<LocalDate, DayUsage> {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    var days by remember { mutableStateOf<Map<LocalDate, DayUsage>>(emptyMap()) }
    LaunchedEffect(resume) {
        while (true) {
            if (Permissions.hasUsageAccess(context)) days = context.graph.usage.loadDays(7)
            delay(60_000)
        }
    }
    return days
}

@Composable
private fun TodayTab(config: AnchorConfig, padding: PaddingValues) {
    val context = LocalContext.current
    val g = context.graph
    val days = rememberWeekUsage()
    val state by g.guard.state.collectAsStateWithLifecycle()
    val resume = rememberResumeCount()
    val stats = remember(resume, state) { g.secure.stats() }
    val today = LocalDate.now()
    val day = days[today]
    val tagged = config.taggedPackages
    val distractingToday = (day?.totalMs(tagged) ?: 0) / 60_000
    val todayStats = stats[today.toEpochDay()] ?: DayStats()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }

    TabColumn(padding) {
        if (!Permissions.hasUsageAccess(context) || !Permissions.hasOverlay(context)) {
            item {
                InfoCard {
                    Text("Protection is limited", style = MaterialTheme.typography.titleSmall)
                    Text("Some permissions are off, so Anchor can't block everything. Open More → Permissions to fix it.")
                }
            }
        }

        item {
            InfoCard {
                Text("Distracting time today", style = MaterialTheme.typography.titleSmall)
                Text(Format.minutes(distractingToday), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                val reduction = UsageAggregator.reductionVsBaseline(distractingToday.toDouble(), config.baselineDailyDistractingMinutes)
                config.baselineDailyDistractingMinutes?.let { base ->
                    Text(
                        when {
                            reduction == null -> ""
                            reduction >= 0 -> "${(reduction * 100).roundToInt()}% below your ${Format.minutes(base.toLong())} daily baseline so far"
                            else -> "${(-reduction * 100).roundToInt()}% above your ${Format.minutes(base.toLong())} daily baseline"
                        },
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Unlocks", day?.unlocks?.toString() ?: "–", Modifier.weight(1f))
                StatTile("First pickup", day?.firstPickup?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "–", Modifier.weight(1f))
                StatTile("Focus", Format.minutes(todayStats.focusMinutes.toLong()), Modifier.weight(1f))
            }
        }

        item {
            InfoCard {
                Text("Interventions today", style = MaterialTheme.typography.titleSmall)
                val rate = if (todayStats.frictionShown > 0) " (${todayStats.backedOut * 100 / todayStats.frictionShown}%)" else ""
                Text("Paused ${todayStats.frictionShown} habitual opens · you backed out of ${todayStats.backedOut}$rate")
                Text("Surfaces blocked ${todayStats.surfaceBlocks}× · doomscroll fuses ${todayStats.doomscrollFuses}")
            }
        }

        // FR-04 instant block
        item {
            InfoCard {
                Text("Block distracting apps now", style = MaterialTheme.typography.titleSmall)
                val until = state.instantBlockUntilMs?.takeIf { it > now }
                if (until != null) {
                    Text("Blocked until ${Format.time(until)}")
                    OutlinedButton(onClick = { g.guard.cancelInstantBlock() }) { Text("Unblock") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            FilledTonalButton(onClick = { g.guard.blockNow(m) }) { Text("$m min") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("This week") }
        item { WeekChart(days, tagged, stats) }

        item { SectionTitle("Tagged apps today") }
        if (config.apps.isEmpty()) item { Text("No apps tagged yet. Add some in Rules.") }
        items(config.apps, key = { "t-" + it.packageName }) { app ->
            val used = day?.minutes(app.packageName) ?: 0
            AppUsageRow(app.packageName, app.label, used, app.dailyLimitMinutes, day?.launches?.get(app.packageName) ?: 0)
        }

        val others = day?.perAppMs.orEmpty().filterKeys { it !in tagged && it != context.packageName }
            .entries.sortedByDescending { it.value }.take(5).filter { it.value >= 60_000 }
        if (others.isNotEmpty()) {
            item { SectionTitle("Other apps") }
            items(others, key = { "o-" + it.key }) { (pkg, ms) ->
                AppUsageRow(pkg, g.apps.label(pkg), ms / 60_000, null, day?.launches?.get(pkg) ?: 0)
            }
        }
    }
}

@Composable
private fun AppUsageRow(pkg: String, label: String, usedMin: Long, limit: Int?, opens: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(pkg)
        Column(Modifier.weight(1f)) {
            Row {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(Format.minutes(usedMin), style = MaterialTheme.typography.bodyLarge)
            }
            if (limit != null) {
                LinearProgressIndicator(
                    progress = { (usedMin.toFloat() / limit).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = if (usedMin >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (usedMin >= limit) "Limit reached · $opens opens" else "${Format.minutes(limit - usedMin)} left of ${Format.minutes(limit.toLong())} · $opens opens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("$opens opens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** FR-01 weekly view: distracting minutes per day, with focus minutes underneath. */
@Composable
private fun WeekChart(days: Map<LocalDate, DayUsage>, tagged: Set<String>, stats: Map<Long, DayStats>) {
    val today = LocalDate.now()
    val range = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = range.map { (days[it]?.totalMs(tagged) ?: 0) / 60_000 }
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(30)
    val summary = range.zip(values).joinToString { (d, v) -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${v} minutes" }
    InfoCard {
        Row(
            Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Distracting minutes per day: $summary" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            range.zip(values).forEach { (d, v) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(if (v > 0) "$v" else "", style = MaterialTheme.typography.labelSmall)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 0.8f))
                            .background(
                                if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                            ),
                    )
                    Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
        val focusWeek = range.sumOf { stats[it.toEpochDay()]?.focusMinutes ?: 0 }
        val streak = focusStreak(stats, today)
        Text("Focus this week: ${Format.minutes(focusWeek.toLong())}" + if (streak > 1) " · $streak-day streak" else "")
    }
}

private fun focusStreak(stats: Map<Long, DayStats>, today: LocalDate): Int {
    var d = today.toEpochDay()
    if ((stats[d]?.focusMinutes ?: 0) == 0) d-- // today not done yet doesn't break the streak
    var n = 0
    while ((stats[d]?.focusMinutes ?: 0) > 0) { n++; d-- }
    return n
}
