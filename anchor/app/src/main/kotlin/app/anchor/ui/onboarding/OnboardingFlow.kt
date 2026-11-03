package app.anchor.ui.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.anchor.anchor.AnchorActivity
import app.anchor.core.model.AnchorConfig
import app.anchor.core.model.Defaults
import app.anchor.core.model.Goal
import app.anchor.core.model.Surface
import app.anchor.core.usage.DayUsage
import app.anchor.core.usage.UsageAggregator
import app.anchor.data.AppInfo
import app.anchor.data.Permissions
import app.anchor.graph
import app.anchor.guard.GuardService
import app.anchor.ui.AppIcon
import app.anchor.ui.Format
import app.anchor.ui.InfoCard
import app.anchor.ui.rememberResumeCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Step { WELCOME, GOAL, USAGE, TAG, OVERLAY, ACCESSIBILITY, RELIABILITY, ANCHOR }

/**
 * Journey A (≤ 4 minutes): goal → usage access → baseline from history and tagging →
 * each remaining permission after its own plain-language disclosure → optional NFC anchor.
 */
@Composable
fun OnboardingFlow(config: AnchorConfig) {
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = context.graph

    fun next() { step = Step.entries[(step.ordinal + 1).coerceAtMost(Step.entries.lastIndex)] }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / Step.entries.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        when (step) {
            Step.WELCOME -> Welcome(onContinue = {
                scope.launch { g.configRepo.update { it.copy(ageConfirmed18 = true) } }
                next()
            })
            Step.GOAL -> GoalStep(config.goal) { goal ->
                scope.launch { g.configRepo.update { it.copy(goal = goal) } }
                next()
            }
            Step.USAGE -> Disclosure(
                title = "See where your time goes",
                body = listOf(
                    "Anchor reads Android's usage statistics: which apps were open, for how long, and when you unlocked the phone.",
                    "We use it for your dashboard, your daily limits and to spot habitual opens. It stays on this phone — it is never uploaded.",
                    "Because Android keeps recent history, your 7-day baseline is ready right away.",
                ),
                granted = { Permissions.hasUsageAccess(context) },
                grantLabel = "Open Usage access settings",
                onGrant = { context.open(Permissions.usageAccessIntent(context), Permissions.usageAccessFallbackIntent()) },
                onContinue = ::next,
                required = true,
            )
            Step.TAG -> TagStep(config.goal) { apps, baseline ->
                scope.launch {
                    g.configRepo.update {
                        it.copy(
                            apps = apps,
                            schedules = if (it.schedules.isEmpty()) Defaults.schedulesFor(it.goal) else it.schedules,
                            baselineDailyDistractingMinutes = baseline,
                            baselineCapturedEpochDay = LocalDate.now().toEpochDay(),
                        )
                    }
                }
                next()
            }
            Step.OVERLAY -> Disclosure(
                title = "Show a pause screen",
                body = listOf(
                    "To block an app, Anchor draws its own screen on top: the block screen, a short breathing pause, the \"what are you opening this for?\" prompt and the fuse timer.",
                    "This needs the \"Display over other apps\" permission. Anchor draws nothing else.",
                ),
                granted = { Permissions.hasOverlay(context) },
                grantLabel = "Allow display over other apps",
                onGrant = { context.open(Permissions.overlayIntent(context)) },
                onContinue = ::next,
                required = true,
            )
            Step.ACCESSIBILITY -> AccessibilityDisclosure(onContinue = ::next)
            Step.RELIABILITY -> ReliabilityStep(onContinue = ::next)
            Step.ANCHOR -> AnchorStep(
                registered = config.anchorRegistered,
                onFinish = {
                    scope.launch {
                        g.configRepo.update { it.copy(onboarded = true) }
                        g.config.first { it?.onboarded == true }
                        GuardService.start(context)
                    }
                },
            )
        }
    }
}

fun Context.open(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        fallback?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Welcome(onContinue: () -> Unit) {
    var adult by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Welcome to Anchor")
        Text(
            "Anchor steps in before a habitual open — not after the damage is done. It blocks the feed, not the whole app, so lectures and messages stay open while Shorts and Reels don't.",
            style = MaterialTheme.typography.bodyLarge,
        )
        InfoCard {
            Text("Private by design", style = MaterialTheme.typography.titleSmall)
            Text("Everything Anchor senses stays on this phone. No account is needed, there are no ads, and screen content is never uploaded.")
        }
        Row(
            Modifier.fillMaxWidth().toggleable(value = adult, role = Role.Checkbox, onValueChange = { adult = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = adult, onCheckedChange = null)
            Text("I am 18 or older", Modifier.padding(start = 8.dp))
        }
        Text(
            "This early version is for adults only. A parent-and-teen mode with verifiable parental consent is planned.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onContinue, enabled = adult, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
    }
}

@Composable
private fun GoalStep(current: Goal, onPick: (Goal) -> Unit) {
    var picked by rememberSaveable { mutableStateOf(current) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("What's your goal?")
        Text("This sets sensible starting rules. You can change everything later.")
        Goal.entries.forEach { goal ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = picked == goal, role = Role.RadioButton, onClick = { picked = goal }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = picked == goal, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(goal.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (goal) {
                            Goal.EXAM -> "CLAT, JEE, NEET, UPSC… Keep lectures, lose the feed. Plans tighten as the exam nears."
                            Goal.DEEP_WORK -> "Protected deep-work blocks on weekdays."
                            Goal.BALANCE -> "Gentle limits and an evening wind-down."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(onClick = { onPick(picked) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun Disclosure(
    title: String,
    body: List<String>,
    granted: () -> Boolean,
    grantLabel: String,
    onGrant: () -> Unit,
    onContinue: () -> Unit,
    required: Boolean,
) {
    val resume = rememberResumeCount() // re-check when returning from Settings
    val isGranted = remember(resume) { granted() }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading(title)
        body.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (isGranted) {
            Text("✓ Granted", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        } else {
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text(grantLabel) }
            if (!required) TextButton(onClick = onContinue) { Text("Skip for now") }
        }
    }
}

/**
 * Play's prominent-disclosure requirement: its own screen, says what is read and how it is used,
 * and needs an affirmative tap before we send the user to Accessibility settings.
 */
@Composable
private fun AccessibilityDisclosure(onContinue: () -> Unit) {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    val granted = remember(resume) { Permissions.hasAccessibility(context) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Block Shorts and Reels, not whole apps")
        Text("Anchor uses Android's Accessibility service. Here is exactly what it does:", style = MaterialTheme.typography.bodyLarge)
        InfoCard {
            Text("What it reads", style = MaterialTheme.typography.titleSmall)
            Text("• Which app and screen is open.\n• In apps you tagged, the screen's layout labels (like \"Shorts\" or \"Reels\") to match the rules you set.\n• That you are scrolling quickly — not what you scroll past.")
        }
        InfoCard {
            Text("How it's used", style = MaterialTheme.typography.titleSmall)
            Text("• To go Back or Home when you open a surface you blocked.\n• To dim the screen and start a fuse when you're doomscrolling.\n• To show the block screen instantly when a blocked app opens.")
        }
        InfoCard {
            Text("What it never does", style = MaterialTheme.typography.titleSmall)
            Text("• It never stores or uploads anything it reads.\n• It does not read passwords, keystrokes or your messages.\n• It never taps or types for you.")
        }
        if (granted) {
            Text("✓ Turned on", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        } else {
            Button(onClick = { context.open(Permissions.accessibilityIntent()) }, modifier = Modifier.fillMaxWidth()) {
                Text("I agree — open Accessibility settings")
            }
            Text(
                "In Settings, open Installed apps → Anchor surface blocker and turn it on. If the switch is greyed out (Android 13+ for apps not installed from Play), open App info → ⋮ → Allow restricted settings first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onContinue) { Text("Not now — block whole apps only") }
        }
    }
}

@Composable
private fun ReliabilityStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifications = remember(resume) { Permissions.hasNotifications(context) }
    val battery = remember(resume) { Permissions.isIgnoringBatteryOptimizations(context) }
    val exact = remember(resume) { Permissions.canScheduleExact(context) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Keep Anchor running")
        Text("Anchor shows one quiet notification while it protects you, and needs to survive your phone's battery saver.", style = MaterialTheme.typography.bodyLarge)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, enabled = !notifications, modifier = Modifier.fillMaxWidth()) {
                Text(if (notifications) "✓ Notifications allowed" else "Allow the status notification")
            }
        }
        OutlinedButton(onClick = { context.open(Permissions.batteryIntent()) }, enabled = !battery, modifier = Modifier.fillMaxWidth()) {
            Text(if (battery) "✓ Battery optimisation off" else "Turn off battery optimisation for Anchor")
        }
        Permissions.exactAlarmIntent(context)?.let { intent ->
            OutlinedButton(onClick = { context.open(intent) }, enabled = !exact, modifier = Modifier.fillMaxWidth()) {
                Text(if (exact) "✓ Schedules start on time" else "Let schedules start on the minute")
            }
        }
        Permissions.oemGuidance()?.let { tip ->
            InfoCard {
                Text("Your phone (${Build.MANUFACTURER}) needs one more step", style = MaterialTheme.typography.titleSmall)
                Text(tip)
                TextButton(onClick = { context.open(Permissions.appDetailsIntent(context)) }) { Text("Open app info") }
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun AnchorStep(registered: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Optional: a physical anchor")
        Text(
            "Stick a cheap NFC tag (₹20–40) in another room, or print a QR code. In Strict mode it's the only way to end a focus session early — willpower alone can't undo it.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (registered) Text("✓ Anchor registered", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        else OutlinedButton(onClick = { AnchorActivity.register(context) }, modifier = Modifier.fillMaxWidth()) { Text("Register an anchor") }
        Text(
            "An accountability partner who holds an override code is coming in a later update.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(if (registered) "Finish" else "Finish without an anchor") }
    }
}

private data class Candidate(val app: AppInfo, val weekMinutes: Long)

/** Journey A steps 2–3: baseline straight from history, then tag apps and surfaces. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagStep(goal: Goal, onDone: (List<app.anchor.core.model.TaggedApp>, Int?) -> Unit) {
    val context = LocalContext.current
    val g = context.graph
    var candidates by remember { mutableStateOf<List<Candidate>?>(null) }
    var days by remember { mutableStateOf<Map<LocalDate, DayUsage>>(emptyMap()) }
    val selected = remember { mutableStateListOf<String>() }
    val surfaces = remember { mutableStateMapOf<String, Set<Surface>>() }

    LaunchedEffect(Unit) {
        val history = if (Permissions.hasUsageAccess(context)) g.usage.loadDays(8) else emptyMap()
        days = history
        val week = HashMap<String, Long>()
        history.values.forEach { d -> d.perAppMs.forEach { (p, ms) -> week.merge(p, ms, Long::plus) } }
        val apps = g.apps.launcherApps()
        candidates = apps.map { Candidate(it, (week[it.packageName] ?: 0) / 60_000) }
            .sortedWith(compareByDescending<Candidate> { it.app.packageName in Defaults.likelyDistracting }.thenByDescending { it.weekMinutes })
        apps.filter { it.packageName in Defaults.likelyDistracting }.forEach {
            selected += it.packageName
            surfaces[it.packageName] = Defaults.defaultSurfaces(it.packageName)
        }
    }

    val today = LocalDate.now()
    val baseline = UsageAggregator.baselineDailyMinutes(days.values, selected.toSet(), today)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Tag what distracts you")
        InfoCard {
            Text("Your 7-day baseline", style = MaterialTheme.typography.titleSmall)
            Text(
                if (baseline == null) "No history yet — your baseline will build as you use the phone."
                else "${Format.minutes(baseline.toLong())} a day in the apps you've tagged. Anchor measures progress against this.",
            )
        }
        val list = candidates
        if (list == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.app.packageName }) { c ->
                    val pkg = c.app.packageName
                    val isSel = pkg in selected
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                                if (isSel) selected -= pkg else {
                                    selected += pkg
                                    surfaces.putIfAbsent(pkg, Defaults.defaultSurfaces(pkg))
                                }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = isSel, onCheckedChange = null)
                            AppIcon(pkg)
                            Column(Modifier.weight(1f)) {
                                Text(c.app.label, style = MaterialTheme.typography.bodyLarge)
                                if (c.weekMinutes > 0) Text("${Format.minutes(c.weekMinutes)} this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val available = Surface.forPackage(pkg)
                        if (isSel && available.isNotEmpty()) {
                            FlowRow(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Block only:", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall)
                                available.forEach { s ->
                                    val on = s in surfaces[pkg].orEmpty()
                                    FilterChip(
                                        selected = on,
                                        onClick = { surfaces[pkg] = if (on) surfaces[pkg].orEmpty() - s else surfaces[pkg].orEmpty() + s },
                                        label = { Text(s.label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                val labels = candidates.orEmpty().associate { it.app.packageName to it.app.label }
                val apps = selected.map { pkg ->
                    Defaults.tagged(pkg, labels[pkg] ?: pkg, goal).copy(blockedSurfaces = surfaces[pkg].orEmpty())
                }
                onDone(apps, baseline)
            },
            enabled = candidates != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (selected.isEmpty()) "Skip for now" else "Tag ${selected.size} apps") }
    }
}
