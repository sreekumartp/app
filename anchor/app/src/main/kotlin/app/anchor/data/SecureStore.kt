package app.anchor.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.anchor.core.policy.RuntimeState
import app.anchor.core.predict.HabitModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class TamperEvent(val atMs: Long, val kind: String, val detail: String)

@Serializable
data class DayStats(
    val frictionShown: Int = 0,
    val backedOut: Int = 0,
    val surfaceBlocks: Int = 0,
    val doomscrollFuses: Int = 0,
    val focusMinutes: Int = 0,
)

/**
 * NFR-07: Strict-mode state, the anchor secret and on-device model live in
 * EncryptedSharedPreferences. Falls back to plain private prefs only if the keystore is broken
 * on a device, and records that as a tamper event so it is visible.
 */
class SecureStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs: SharedPreferences
    private val encrypted: Boolean

    init {
        var ok = true
        prefs = try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "anchor_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e("Anchor", "Encrypted prefs unavailable", e)
            ok = false
            context.getSharedPreferences("anchor_secure_fallback", Context.MODE_PRIVATE)
        }
        encrypted = ok
        if (!ok) logTamper("storage", "Encrypted storage unavailable on this device")
    }

    // Runtime state (focus session, fuses, passes, instant block)
    fun loadRuntime(): RuntimeState =
        prefs.getString(K_RUNTIME, null)?.let { runCatching { json.decodeFromString(RuntimeState.serializer(), it) }.getOrNull() } ?: RuntimeState()

    fun saveRuntime(state: RuntimeState) = prefs.edit().putString(K_RUNTIME, json.encodeToString(RuntimeState.serializer(), state)).apply()

    // IN-01 on-device model
    fun loadModel(): HabitModel =
        prefs.getString(K_MODEL, null)?.let { runCatching { json.decodeFromString(HabitModel.serializer(), it) }.getOrNull() } ?: HabitModel()

    fun saveModel(model: HabitModel) = prefs.edit().putString(K_MODEL, json.encodeToString(HabitModel.serializer(), model)).apply()

    // IN-04 anchor: only a salted hash of the NFC tag UID or QR secret is stored.
    fun setAnchor(kind: String, secret: ByteArray) {
        prefs.edit().putString(K_ANCHOR_KIND, kind).putString(K_ANCHOR_HASH, hash(secret)).apply()
    }

    fun anchorKind(): String? = prefs.getString(K_ANCHOR_KIND, null)

    fun matchesAnchor(secret: ByteArray): Boolean {
        val stored = prefs.getString(K_ANCHOR_HASH, null) ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(secret).toByteArray())
    }

    private fun hash(secret: ByteArray): String {
        val salt = prefs.getString(K_SALT, null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString(K_SALT, it).commit() }
        val d = MessageDigest.getInstance("SHA-256").digest(salt.toByteArray() + secret)
        return d.joinToString("") { "%02x".format(it) }
    }

    // Tamper and gap log (shown in the app; reported to the partner in phase 2)
    fun tamperLog(): List<TamperEvent> =
        prefs.getString(K_TAMPER, null)?.let { runCatching { json.decodeFromString(ListSerializer(TamperEvent.serializer()), it) }.getOrNull() }.orEmpty()

    @Synchronized
    fun logTamper(kind: String, detail: String, atMs: Long = System.currentTimeMillis()) {
        val list = (tamperLog() + TamperEvent(atMs, kind, detail)).takeLast(100)
        prefs.edit().putString(K_TAMPER, json.encodeToString(ListSerializer(TamperEvent.serializer()), list)).apply()
    }

    // Heartbeat for the WorkManager watchdog (NFR-04)
    var heartbeatMs: Long
        get() = prefs.getLong(K_HEARTBEAT, 0)
        set(v) = prefs.edit().putLong(K_HEARTBEAT, v).apply()

    // Per-day KPI counters, keyed by epoch day
    private val statsSerializer = MapSerializer(Long.serializer(), DayStats.serializer())

    fun stats(): Map<Long, DayStats> =
        prefs.getString(K_STATS, null)?.let { runCatching { json.decodeFromString(statsSerializer, it) }.getOrNull() }.orEmpty()

    @Synchronized
    fun bumpStats(epochDay: Long, change: (DayStats) -> DayStats) {
        val all = stats().toMutableMap()
        all[epochDay] = change(all[epochDay] ?: DayStats())
        val trimmed = all.filterKeys { it > epochDay - 60 }
        prefs.edit().putString(K_STATS, json.encodeToString(statsSerializer, trimmed)).apply()
    }

    private companion object {
        const val K_RUNTIME = "runtime"
        const val K_MODEL = "habit_model"
        const val K_ANCHOR_KIND = "anchor_kind"
        const val K_ANCHOR_HASH = "anchor_hash"
        const val K_SALT = "salt"
        const val K_TAMPER = "tamper"
        const val K_HEARTBEAT = "heartbeat"
        const val K_STATS = "stats"
    }
}
