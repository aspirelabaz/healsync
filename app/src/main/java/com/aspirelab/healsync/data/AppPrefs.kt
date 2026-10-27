package com.aspirelab.healsync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Context.dataStore by preferencesDataStore("healsync")

class AppPrefs(context: Context) {
    private val store = context.applicationContext.dataStore

    val isSignedIn: Flow<Boolean> = store.data.map { it[AUTH_STATE] != null }
    val lastSync: Flow<Instant?> = store.data.map { prefs -> prefs[LAST_SYNC]?.let(Instant::ofEpochMilli) }
    val log: Flow<List<String>> = store.data.map { prefs ->
        prefs[LOG]?.split(LOG_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun authStateJson(): String? =
        store.data.first()[AUTH_STATE]?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() }

    suspend fun saveAuthState(json: String?) {
        store.edit { prefs ->
            if (json == null) prefs.remove(AUTH_STATE) else prefs[AUTH_STATE] = SecretBox.encrypt(json)
        }
    }

    suspend fun lastSyncNow(): Instant? = lastSync.first()

    suspend fun saveLastSync(instant: Instant) {
        store.edit { it[LAST_SYNC] = instant.toEpochMilli() }
    }

    suspend fun appendLog(message: String) {
        val line = "${TIME_FORMAT.format(Instant.now())}  ${message.replace(Regex("\\s+"), " ")}"
        store.edit { prefs ->
            val lines = prefs[LOG]?.split(LOG_SEPARATOR).orEmpty()
            prefs[LOG] = (listOf(line) + lines).take(MAX_LOG_LINES).joinToString(LOG_SEPARATOR)
        }
    }

    private companion object {
        val AUTH_STATE = stringPreferencesKey("auth_state")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val LOG = stringPreferencesKey("log")
        const val LOG_SEPARATOR = "\n"
        const val MAX_LOG_LINES = 100
        val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}
