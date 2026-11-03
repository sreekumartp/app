package app.anchor

import android.app.Application
import android.content.Context
import app.anchor.core.model.AnchorConfig
import app.anchor.core.surface.RulePack
import app.anchor.core.surface.RulePackLoader
import app.anchor.data.ConfigRepository
import app.anchor.data.InstalledApps
import app.anchor.data.SecureStore
import app.anchor.data.UsageRepository
import app.anchor.guard.Guard
import app.anchor.guard.Notifications
import app.anchor.guard.WatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AnchorApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        WatchdogWorker.schedule(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as AnchorApp).graph

/** Manual dependency graph (the spec's Hilt would slot in here without changing call sites). */
class AppGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val configRepo = ConfigRepository(app)
    val secure = SecureStore(app)
    val usage = UsageRepository(app)
    val apps = InstalledApps(app)

    /** Null until DataStore has loaded, so the UI never flashes onboarding for an onboarded user. */
    val config: StateFlow<AnchorConfig?> = configRepo.config.stateIn(scope, SharingStarted.Eagerly, null)

    val guard = Guard(app, secure)

    /** FR-05 detectors: the bundled pack; signed remote packs replace it via RulePackLoader.loadRemote. */
    val rulePack: RulePack by lazy {
        app.assets.open("rulepack.json").bufferedReader().use { RulePackLoader.parseBundled(it.readText()) }
    }
}
