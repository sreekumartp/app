package app.anchor.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.anchor.core.doomscroll.DoomscrollDetector
import app.anchor.core.model.AnchorConfig
import app.anchor.core.policy.Decision
import app.anchor.core.policy.LaunchContext
import app.anchor.core.policy.PolicyEngine
import app.anchor.core.predict.LaunchFeatures
import app.anchor.core.predict.LaunchOutcome
import app.anchor.data.Permissions
import app.anchor.graph
import app.anchor.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * One foreground service (specialUse) hosts the policy engine (section 6). It receives foreground
 * and scroll events from the accessibility service (falling back to UsageStats polling), asks the
 * [PolicyEngine] for a decision, and drives the overlay. Models score; only the engine decides.
 */
class GuardService : LifecycleService(), OverlayActions {

    private val g by lazy { graph }
    private lateinit var overlay: OverlayController
    private lateinit var engine: PolicyEngine
    private val doom = DoomscrollDetector()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    @Volatile private var config: AnchorConfig = AnchorConfig()
    private var current: String? = null
    private var previous: String? = null
    private var launch: LaunchRecord? = null
    private var usageToday: Map<String, Long> = emptyMap()
    private var usageFetchedAt = 0L
    private var lastUnlockMs: Long? = null
    private var screenOnSinceMs: Long = System.currentTimeMillis()
    private var lastHeartbeat = 0L
    private var lastSurfacePublish = 0L
    private val launchHistory = ArrayDeque<Pair<String, Long>>()
    private val sessionLengths = HashMap<String, ArrayDeque<Long>>()

    /** One launch of a tagged app, kept so the open can be self-labelled when it ends (IN-01). */
    private data class LaunchRecord(val pkg: String, val startMs: Long, val features: LaunchFeatures?, var frictionShown: Boolean = false)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    endLaunch(now)
                    previous = current
                    current = null
                    doom.reset()
                    overlay.hideAll()
                }
                Intent.ACTION_SCREEN_ON -> screenOnSinceMs = now
                Intent.ACTION_USER_PRESENT -> {
                    lastUnlockMs = now
                    // The app under the lock screen comes back without a window change event.
                    lifecycleScope.launch {
                        val pkg = GuardBus.accessibility?.activePackage() ?: withContext(Dispatchers.IO) { g.usage.currentForeground() }
                        pkg?.let { onForeground(it, System.currentTimeMillis()) }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        overlay = OverlayController(this, this)
        engine = buildEngine()
        goForeground()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        lifecycleScope.launch {
            lastUnlockMs = withContext(Dispatchers.IO) { runCatching { g.usage.lastUnlockMs() }.getOrNull() }
        }

        // Config and runtime state changes: refresh notification, alarms and surface rules.
        lifecycleScope.launch {
            combine(g.config.filterNotNull(), g.guard.state) { c, s -> c to s }.collect { (c, s) ->
                config = c
                doom.triggerSwipes = c.interventions.doomscrollSwipes
                if (!c.onboarded) {
                    stopSelf()
                    return@collect
                }
                publishSurfaces(System.currentTimeMillis())
                updateNotification()
                AlarmScheduler.reschedule(this@GuardService)
                reevaluate()
            }
        }

        lifecycleScope.launch {
            GuardBus.events.collect { e ->
                when (e) {
                    is GuardBus.Event.Foreground -> onForeground(e.packageName, e.atMs)
                    is GuardBus.Event.Scroll -> onScroll(e.packageName, e.atMs)
                    is GuardBus.Event.SurfaceBlocked -> onSurfaceBlocked(e)
                    GuardBus.Event.Poke -> reevaluate()
                }
            }
        }

        lifecycleScope.launch { tickLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(screenReceiver) }
        overlay.destroy()
        super.onDestroy()
    }

    private fun buildEngine(): PolicyEngine {
        val always = mutableSetOf(packageName, "com.android.systemui", "com.android.phone", "com.android.server.telecom", "com.google.android.dialer", "com.android.dialer")
        g.apps.homePackage()?.let(always::add)
        runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let(always::add)
        val strictGuarded = setOf(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.miui.securitycenter", "com.coloros.safecenter",
        )
        return PolicyEngine(alwaysAllowed = always - strictGuarded, strictGuarded = strictGuarded)
    }

    private fun goForeground() {
        val n = Notifications.guard(this, g.guard.state.value, config.apps.size, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, Notifications.GUARD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.GUARD_ID, n)
        }
    }

    private fun updateNotification() {
        if (!Permissions.hasNotifications(this)) return
        val n = Notifications.guard(this, g.guard.state.value, config.apps.size, System.currentTimeMillis())
        runCatching { NotificationManagerCompat.from(this).notify(Notifications.GUARD_ID, n) }
    }

    // --- Event handling -------------------------------------------------------------------

    private fun onForeground(pkg: String, now: Long) {
        if (pkg == current) return
        endLaunch(now)
        previous = current
        current = pkg
        doom.reset(pkg)
        overlay.clearDim()

        launchHistory.addLast(pkg to now)
        while (launchHistory.isNotEmpty() && now - launchHistory.first().second > HOUR) launchHistory.removeFirst()

        val tagged = pkg in config.taggedPackages
        if (tagged) refreshUsage(now, force = true)
        val features = if (tagged) features(pkg, now) else null
        launch = LaunchRecord(pkg, now, features)
        val score = features?.takeIf { config.interventions.predictionEnabled }?.let { g.guard.model.score(it) }
        evaluate(now, score)
    }

    private fun onScroll(pkg: String, now: Long) {
        if (pkg != current || !config.interventions.doomscrollEnabled) return
        if (pkg == packageName || pkg == g.apps.homePackage()) return
        val st = doom.onScroll(pkg, now)
        if (!st.triggered) return
        overlay.setDim(st.dimLevel)
        // IN-03: the fuse appears once per streak; when it runs out the engine blocks the app.
        if (g.guard.state.value.fuses[pkg] == null) {
            g.guard.startFuse(pkg, DOOMSCROLL_FUSE_MINUTES)
            g.secure.bumpStats(today()) { it.copy(doomscrollFuses = it.doomscrollFuses + 1) }
        }
    }

    private fun onSurfaceBlocked(e: GuardBus.Event.SurfaceBlocked) {
        g.secure.bumpStats(today()) { it.copy(surfaceBlocks = it.surfaceBlocks + 1) }
        Toast.makeText(this, "${e.surface.label} is blocked — the rest of ${g.apps.label(e.packageName)} stays open", Toast.LENGTH_SHORT).show()
    }

    private fun endLaunch(now: Long) {
        val l = launch ?: return
        launch = null
        val seconds = (now - l.startMs) / 1000
        sessionLengths.getOrPut(l.pkg) { ArrayDeque() }.apply {
            addLast(seconds)
            while (size > 5) removeFirst()
        }
        // Self-supervised label from session length when no friction answered it already.
        if (!l.frictionShown && l.features != null) g.guard.learn(l.features, LaunchOutcome.Session(seconds))
    }

    private fun features(pkg: String, now: Long): LaunchFeatures {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val battery = getSystemService(BatteryManager::class.java)
        val recent = sessionLengths[pkg]
        return LaunchFeatures(
            secondsSinceUnlock = lastUnlockMs?.let { (now - it) / 1000 } ?: 3_600,
            previousWasTagged = previous in config.taggedPackages,
            previousWasLauncher = previous == null || previous == g.apps.homePackage(),
            launchesOfThisAppLastHour = launchHistory.count { it.first == pkg } - 1,
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            charging = battery?.isCharging == true,
            screenOnMinutes = (now - screenOnSinceMs) / 60_000,
            meanRecentSessionSeconds = recent?.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    // --- Decisions ------------------------------------------------------------------------

    private fun reevaluate() = evaluate(System.currentTimeMillis(), score = null)

    /** [score] is non-null only at launch, so friction is shown once per open, never on ticks. */
    private fun evaluate(now: Long, score: Double?) {
        val pkg = current ?: return
        val state = g.guard.state.value
        val decision = engine.decide(config, state, LaunchContext(pkg, now, zone, usageToday, score))
        val shown = overlay.main
        when (decision) {
            is Decision.Block -> {
                overlay.clearDim()
                overlay.hideFuse()
                overlay.showMain(
                    MainOverlay.Block(
                        pkg = pkg,
                        appLabel = g.apps.label(pkg),
                        reason = decision.reason,
                        untilMs = decision.untilMs,
                        detail = decision.detail,
                        focusSession = state.focus != null,
                    ),
                )
            }
            is Decision.Friction -> {
                launch?.frictionShown = true
                g.secure.bumpStats(today()) { it.copy(frictionShown = it.frictionShown + 1) }
                overlay.showMain(MainOverlay.Friction(pkg, g.apps.label(pkg), decision.level, decision.breathingSeconds, Purposes.forPackage(pkg)))
            }
            is Decision.AllowWithFuse -> {
                if (shown !is MainOverlay.Friction || shown.pkg != pkg) overlay.hideMain()
                overlay.showFuse(decision.fuseEndsAtMs)
            }
            Decision.Allow -> {
                // Keep a friction prompt the user has not answered yet.
                if (shown !is MainOverlay.Friction || shown.pkg != pkg) overlay.hideMain()
                overlay.hideFuse()
            }
        }
    }

    private suspend fun tickLoop() {
        while (lifecycleScope.isActive) {
            val now = System.currentTimeMillis()
            g.guard.completeFocusIfDone(now)
            g.guard.prune(now, engine.fuseCooldownMs)

            if (now - lastHeartbeat > 60_000) {
                g.secure.heartbeatMs = now
                lastHeartbeat = now
            }
            if (now - lastSurfacePublish > 10_000) publishSurfaces(now)

            // Fallback when the accessibility service is not running: poll usage events.
            if (GuardBus.accessibility == null && Permissions.hasUsageAccess(this)) {
                withContext(Dispatchers.IO) { g.usage.currentForeground() }?.let { onForeground(it, now) }
            }

            val pkg = current
            if (pkg != null && (pkg in config.taggedPackages || g.guard.state.value.let { it.focus != null || pkg in it.fuses })) {
                refreshUsage(now, force = false)
                reevaluate()
            }
            doom.onTick(now)
            delay(1_000)
        }
    }

    private fun refreshUsage(now: Long, force: Boolean) {
        if (!force && now - usageFetchedAt < 10_000) return
        if (!Permissions.hasUsageAccess(this)) return
        usageToday = runCatching { g.usage.todayUsageMs(zone) }.getOrElse { usageToday }
        usageFetchedAt = now
    }

    private fun publishSurfaces(now: Long) {
        lastSurfacePublish = now
        val state = g.guard.state.value
        val pkgs = config.taggedPackages + state.passSurfaces.keys
        GuardBus.blockedSurfaces.value = pkgs.associateWith { engine.blockedSurfaces(config, state, it, now) }.filterValues { it.isNotEmpty() }
    }

    private fun today() = LocalDate.now(zone).toEpochDay()

    // --- Overlay actions ------------------------------------------------------------------

    override fun goHome() {
        overlay.hideMain()
        val a11y = GuardBus.accessibility
        if (a11y == null || !a11y.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
            runCatching {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    override fun openAnchor() {
        overlay.hideMain()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun continueAfterGate(pkg: String) {
        // Let the open proceed; it is labelled later from how long the session lasts.
        launch?.takeIf { it.pkg == pkg }?.frictionShown = false
        g.guard.grantPass(pkg, PASS_AFTER_GATE_MINUTES)
        overlay.hideMain()
    }

    override fun backOut(pkg: String) {
        labelLaunch(pkg, LaunchOutcome.BackedOut)
        g.secure.bumpStats(today()) { it.copy(backedOut = it.backedOut + 1) }
        goHome()
    }

    override fun statePurpose(pkg: String, purpose: Purpose) {
        labelLaunch(pkg, LaunchOutcome.StatedPurpose)
        g.guard.grantPass(pkg, purpose.minutes, purpose.extraBlockedSurfaces)
        overlay.hideMain()
    }

    override fun justBrowsing(pkg: String) {
        labelLaunch(pkg, LaunchOutcome.JustBrowsing)
        g.guard.startFuse(pkg, config.interventions.browsingFuseMinutes)
        overlay.hideMain()
    }

    override fun markIntentional(pkg: String) {
        labelLaunch(pkg, LaunchOutcome.MarkedIntentional)
        g.guard.grantPass(pkg, PASS_INTENTIONAL_MINUTES)
        overlay.hideMain()
    }

    private fun labelLaunch(pkg: String, outcome: LaunchOutcome) {
        val l = launch?.takeIf { it.pkg == pkg } ?: return
        l.features?.let { g.guard.learn(it, outcome) }
        l.frictionShown = true
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val DOOMSCROLL_FUSE_MINUTES = 5
        private const val PASS_AFTER_GATE_MINUTES = 10
        private const val PASS_INTENTIONAL_MINUTES = 30

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            } catch (e: Exception) {
                // Android 12+ can refuse background FGS starts; the watchdog logs the gap and retries.
                Log.w("Anchor", "Could not start guard service", e)
            }
        }
    }
}
