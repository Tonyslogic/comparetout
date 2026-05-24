package com.tfcode.comparetout.ui2

import android.content.Context
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's preferred timezone.
 *
 * Internally the app stores everything in UTC. This store decides which
 * IANA zone to use when:
 *   • interpreting imported timestamps that don't carry zone info
 *   • rendering UTC-anchored values back to the user
 *
 * Stored value is the IANA zone ID (e.g. `Europe/Dublin`). An empty / missing
 * value means "use the phone's current default" — that lookup happens in
 * [zoneId] / [resolvedZone] so a travelling user picks up their new device
 * zone automatically.
 *
 * Pattern mirrors [FavouritePlanStore]: Hilt singleton, StateFlow for live
 * reads, DataStore-backed persistence via [TOUTCApplication].
 */
@Singleton
class UserTimezoneStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val app: TOUTCApplication
        get() = context.applicationContext as TOUTCApplication

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The raw stored value: a ZoneId string when the user has made an
     * explicit choice, or `null` when no preference is recorded (fall back
     * to device default).
     */
    private val _zoneId = MutableStateFlow<String?>(null)
    val zoneId: StateFlow<String?> = _zoneId.asStateFlow()

    @Volatile private var loaded = false

    /** Load the persisted choice into [zoneId]; idempotent. */
    suspend fun ensureLoaded() {
        if (loaded) return
        val s = withContext(Dispatchers.IO) {
            runCatching { app.getStringValueFromDataStore(KEY) }.getOrDefault("")
        }
        _zoneId.value = s.takeIf { it.isNotBlank() }
        loaded = true
    }

    /**
     * Set the active zone. Passing `null` clears the preference and lets the
     * app fall back to the device default — useful for the "Use device
     * timezone" reset.
     */
    fun setZone(id: ZoneId?) {
        _zoneId.value = id?.id
        scope.launch {
            runCatching { app.putStringValueIntoDataStore(KEY, id?.id ?: "") }
        }
    }

    /**
     * Resolve the effective ZoneId — the stored choice if valid, otherwise
     * the phone's current default. Safe to call from any thread once
     * [ensureLoaded] has returned.
     */
    fun resolvedZone(): ZoneId {
        val raw = _zoneId.value
        if (raw.isNullOrBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(raw) }.getOrElse { ZoneId.systemDefault() }
    }

    private companion object { const val KEY = "user_timezone" }
}
