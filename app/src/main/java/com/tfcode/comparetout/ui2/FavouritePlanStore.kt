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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton that persists the user's "current supplier plan" choice (the
 * favourite). Backed by the same DataStore that hosts the other UI2 prefs.
 *
 * Every VM that cares about the favourite reads from [id]; updates flow
 * through [setFavourite] so list / dashboard / compare stay in sync without
 * polling the DB. When the underlying plan is deleted, callers should
 * pass the still-known plan ids into [reconcile] so a stale favourite
 * is automatically cleared.
 */
@Singleton
class FavouritePlanStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val app: TOUTCApplication
        get() = context.applicationContext as TOUTCApplication

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _id = MutableStateFlow<Long?>(null)
    val id: StateFlow<Long?> = _id.asStateFlow()

    @Volatile private var loaded = false

    /**
     * Ensure the stored value has been read from disk into [id]. Safe to call
     * from any thread; subsequent calls are no-ops.
     */
    suspend fun ensureLoaded() {
        if (loaded) return
        val s = withContext(Dispatchers.IO) {
            runCatching { app.getStringValueFromDataStore(KEY) }
                .getOrDefault("")
        }
        _id.value = s.toLongOrNull()
        loaded = true
    }

    /** Set the favourite to [planId], or `null` to clear it. */
    fun setFavourite(planId: Long?) {
        _id.value = planId
        scope.launch {
            runCatching {
                app.putStringValueIntoDataStore(KEY, planId?.toString() ?: "")
            }
        }
    }

    /**
     * Drop the favourite if it no longer corresponds to one of [knownPlanIds].
     * Call this whenever the list of plans changes — covers the case where
     * the user deletes the favourited plan (here or elsewhere in the app).
     */
    fun reconcile(knownPlanIds: Collection<Long>) {
        val current = _id.value ?: return
        if (current !in knownPlanIds) setFavourite(null)
    }

    private companion object { const val KEY = "favourite_price_plan_id" }
}
