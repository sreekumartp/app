package app.anchor.guard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.anchor.core.policy.BlockReason
import app.anchor.core.predict.InterventionLevel
import app.anchor.ui.Format
import kotlinx.coroutines.delay

@Composable
fun OverlayContent(overlay: MainOverlay?, actions: OverlayActions) {
    when (overlay) {
        null -> Unit
        is MainOverlay.Block -> BlockScreen(overlay, actions)
        is MainOverlay.Friction -> FrictionScreen(overlay, actions)
    }
}

@Composable
private fun OverlayScaffold(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

/** FR-04: full-app block showing the reason and time left. There is no "ignore" button. */
@Composable
private fun BlockScreen(b: MainOverlay.Block, actions: OverlayActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(b) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    OverlayScaffold {
        Text(b.appLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            b.reason.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        b.untilMs?.let { until ->
            val left = until - now
            Text(
                if (left > 60 * 60_000) "Opens again at ${Format.time(until)}" else "Opens again in ${Format.clock(left)}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        b.detail?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
        Text(reasonHint(b.reason), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (b.focusSession) {
            Button(onClick = actions::openAnchor, modifier = Modifier.fillMaxWidth()) { Text("Back to my focus session") }
            OutlinedButton(onClick = actions::goHome, modifier = Modifier.fillMaxWidth()) { Text("Go home") }
        } else {
            Button(onClick = actions::goHome, modifier = Modifier.fillMaxWidth()) { Text("Go home") }
        }
    }
}

private fun reasonHint(r: BlockReason) = when (r) {
    BlockReason.FOCUS -> "Only the apps you allowed for this session can open."
    BlockReason.INSTANT -> "You chose to block distracting apps for a while."
    BlockReason.SCHEDULE -> "This is one of your scheduled blocks."
    BlockReason.EXAM_LOCKDOWN -> "Mock-test day: a full lockdown window, like the real exam."
    BlockReason.DAILY_LIMIT -> "You've used today's limit for this app."
    BlockReason.LEISURE_BUDGET -> "Your exam plan sets today's leisure time. It resets at midnight."
    BlockReason.FUSE_EXPIRED -> "Take a break from the feed. Messages and calls still work in other apps."
}

/**
 * Journey B: a 3–5 s breathing gate, then — for likely habitual opens — the intent prompt
 * ("What are you opening this for?").
 */
@Composable
private fun FrictionScreen(f: MainOverlay.Friction, actions: OverlayActions) {
    var remaining by remember(f) { mutableIntStateOf(f.breathingSeconds) }
    var gateDone by remember(f) { mutableStateOf(false) }
    LaunchedEffect(f) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        gateDone = true
    }
    OverlayScaffold {
        if (!gateDone) {
            Breathing()
            Text(
                "Take a breath before opening ${f.appLabel}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text("$remaining", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { actions.backOut(f.pkg) }) { Text("I don't need it — go home") }
        } else if (f.level == InterventionLevel.BREATHING_GATE) {
            Text("Still want ${f.appLabel}?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Button(onClick = { actions.continueAfterGate(f.pkg) }, modifier = Modifier.fillMaxWidth()) { Text("Open ${f.appLabel}") }
            OutlinedButton(onClick = { actions.backOut(f.pkg) }, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        } else {
            Text(
                "What are you opening ${f.appLabel} for?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            f.purposes.forEach { p ->
                Button(onClick = { actions.statePurpose(f.pkg, p) }, modifier = Modifier.fillMaxWidth()) {
                    Text(p.label, textAlign = TextAlign.Center)
                }
                if (p.extraBlockedSurfaces.isNotEmpty()) {
                    Text(
                        "${p.minutes} min · ${p.extraBlockedSurfaces.joinToString { it.label }} stay blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = { actions.justBrowsing(f.pkg) }, modifier = Modifier.fillMaxWidth()) { Text("Just browsing (starts a fuse)") }
            OutlinedButton(onClick = { actions.backOut(f.pkg) }, modifier = Modifier.fillMaxWidth()) { Text("Never mind") }
            TextButton(onClick = { actions.markIntentional(f.pkg) }) { Text("This was intentional — don't ask like this") }
        }
    }
}

@Composable
private fun Breathing() {
    val t = rememberInfiniteTransition(label = "breath")
    val scale by t.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        Modifier.size(160.dp).scale(scale).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    )
}

/** Journey B / IN-03 fuse timer, drawn in a touch-through window. */
@Composable
fun FusePill(endsAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
    ) {
        Text(
            "Fuse · ${Format.clock(endsAtMs - now)} left",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
