package app.anchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.anchor.core.model.AnchorConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.configStore by preferencesDataStore(name = "anchor_config")

/** User configuration (rules, schedules, exam plan). Stored on device only (NFR-06). */
class ConfigRepository(private val context: Context) {
    private val key = stringPreferencesKey("config_json")

    val config: Flow<AnchorConfig> = context.configStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(AnchorConfig.serializer(), it) }.getOrNull() } ?: AnchorConfig()
    }

    suspend fun current(): AnchorConfig = config.first()

    suspend fun update(transform: (AnchorConfig) -> AnchorConfig) {
        context.configStore.edit { prefs ->
            val now = prefs[key]?.let { runCatching { json.decodeFromString(AnchorConfig.serializer(), it) }.getOrNull() } ?: AnchorConfig()
            prefs[key] = json.encodeToString(AnchorConfig.serializer(), transform(now))
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
