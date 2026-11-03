package app.anchor.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.anchor.anchor.AnchorActivity
import app.anchor.core.model.AnchorConfig
import app.anchor.data.Permissions
import app.anchor.graph
import app.anchor.guard.GuardBus
import app.anchor.guard.GuardService
import app.anchor.ui.InfoCard
import app.anchor.ui.PermissionRow
import app.anchor.ui.SectionTitle
import app.anchor.ui.onboarding.open
import app.anchor.ui.rememberResumeCount
import java.text.DateFormat
import java.util.Date

@Composable
fun MoreTab(config: AnchorConfig, padding: PaddingValues) {
    val context = LocalContext.current
    val g = context.graph
    val resume = rememberResumeCount()
    val state by g.guard.state.collectAsStateWithLifecycle()
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val tamper = remember(resume) { g.secure.tamperLog().asReversed() }
    val strictActive = state.focus?.strict == true

    TabColumn(padding) {
        item { SectionTitle("Permissions") }
        item {
            InfoCard {
                PermissionRow("Usage access", remember(resume) { Permissions.hasUsageAccess(context) }) {
                    context.open(Permissions.usageAccessIntent(context), Permissions.usageAccessFallbackIntent())
                }
                PermissionRow("Display over other apps", remember(resume) { Permissions.hasOverlay(context) }) {
                    context.open(Permissions.overlayIntent(context))
                }
                PermissionRow("Accessibility (surface blocking)", remember(resume) { Permissions.hasAccessibility(context) }) {
                    context.open(Permissions.accessibilityIntent())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow("Status notification", remember(resume) { Permissions.hasNotifications(context) }) {
                        notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                PermissionRow("No battery optimisation", remember(resume) { Permissions.isIgnoringBatteryOptimizations(context) }) {
                    context.open(Permissions.batteryIntent())
                }
                Permissions.exactAlarmIntent(context)?.let { intent ->
                    PermissionRow("On-time schedules", remember(resume) { Permissions.canScheduleExact(context) }) { context.open(intent) }
                }
                Text(
                    if (GuardService.running) "Protection service is running." else "Protection service is stopped.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!GuardService.running) TextButton(onClick = { GuardService.start(context) }) { Text("Start it") }
            }
        }
        Permissions.oemGuidance()?.let { tip ->
            item {
                InfoCard {
                    Text("Keep Anchor alive on ${Build.MANUFACTURER}", style = MaterialTheme.typography.titleSmall)
                    Text(tip)
                    TextButton(onClick = { context.open(Permissions.appDetailsIntent(context)) }) { Text("Open app info") }
                }
            }
        }

        item { SectionTitle("Physical anchor") }
        item {
            InfoCard {
                val kind = remember(resume) { g.secure.anchorKind() }
                Text(
                    when {
                        !config.anchorRegistered || kind == null -> "No anchor registered. Strict mode needs one."
                        kind == "nfc" -> "NFC tag registered."
                        else -> "Printed QR code registered."
                    },
                )
                OutlinedButton(onClick = { AnchorActivity.register(context) }, enabled = !strictActive) {
                    Text(if (config.anchorRegistered) "Replace anchor" else "Register an anchor")
                }
                if (strictActive) Text("You can't change the anchor during a Strict session.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionTitle("On-device learning") }
        item {
            InfoCard {
                Text("The habitual-open model has learned from ${g.guard.model.updates} of your app opens. It runs and trains only on this phone.")
                TextButton(onClick = { g.guard.resetModel() }) { Text("Reset what it learned") }
                Text("Surface rules: v${g.rulePack.version}", style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionTitle("Tamper and gap log") }
        if (tamper.isEmpty()) item { Text("Nothing logged. Clock changes and periods when protection was off appear here.") }
        items(tamper.take(30)) { e ->
            Row {
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.atMs)) + " · " + e.detail, style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionTitle("Privacy") }
        item {
            InfoCard {
                Text(
                    "Usage events, accessibility data and screen text never leave this phone. Anchor has no account, no analytics and no ads. " +
                        "Your rules live in app storage; Strict-mode state, your anchor and the model are encrypted with a key in the Android Keystore.",
                )
                Text(
                    if (GuardBus.accessibility != null) "Accessibility service: connected (observe-only)." else "Accessibility service: off — whole-app blocking still works.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
