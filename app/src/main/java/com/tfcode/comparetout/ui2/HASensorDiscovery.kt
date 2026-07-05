package com.tfcode.comparetout.ui2

import com.tfcode.comparetout.importers.homeassistant.BatterySensor
import com.tfcode.comparetout.importers.homeassistant.DeviceSensor
import com.tfcode.comparetout.importers.homeassistant.EnergySensors
import com.tfcode.comparetout.importers.homeassistant.HADispatcher
import com.tfcode.comparetout.importers.homeassistant.MessageHandler
import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsRequest
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage
import com.tfcode.comparetout.importers.homeassistant.messages.StatsForPeriodRequest
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK
import com.tfcode.comparetout.importers.homeassistant.messages.energyPrefsResult.EnergyPrefsResult
import com.tfcode.comparetout.importers.homeassistant.messages.statsForPeriodResult.StatsForPeriodResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume

// ──────────────────────────────────────────────────────────────────────────
// Kotlin coroutine wrapper around the legacy Java HADispatcher. Lets the
// UI2 data-source management screen perform credential validation + energy-
// sensor discovery without the Fragment lifecycle the legacy ImportHAOverview
// uses.
//
// The WebSocket lifecycle is short-lived: connect → auth → energy/get_prefs
// → parse → range probe → close. We just translate that into a suspending
// function returning an [HADiscoveryResult].
// ──────────────────────────────────────────────────────────────────────────

/**
 * Result of a discovery attempt. Exactly one of [sensors] / [error] is set.
 * If authentication failed, [error] reads "Invalid credentials".
 *
 * [serverStart]/[serverEnd] are the first/last days the HA recorder has
 * statistics for the discovered sensors — best-effort: null when the range
 * probe fails or the server has no data yet, never a reason to fail an
 * otherwise successful discovery.
 */
data class HADiscoveryResult(
    val sensors: EnergySensors?,
    val error: String?,
    val serverStart: LocalDate? = null,
    val serverEnd: LocalDate? = null
)

/**
 * Connect to a Home Assistant WebSocket endpoint, authenticate with [token],
 * fetch the user's energy preferences, and return the parsed sensor set plus
 * (best-effort) the first/last day the recorder holds statistics for them.
 *
 * The dispatcher itself wires up the auth handshake (auth_required → auth →
 * auth_ok | auth_invalid). We layer two extra handlers on top:
 *
 *  - auth_ok: send an `energy/get_prefs` request
 *  - auth_invalid: resume with an error result
 *
 * Both branches close the socket so we don't leak the OkHttp connection.
 */
suspend fun discoverHASensors(host: String, token: String): HADiscoveryResult =
    suspendCancellableCoroutine { cont ->
        val dispatcher = HADispatcher(host, token)
        var resumed = false
        // Set once the energy prefs have parsed. A socket drop during the
        // (optional) range probe must not turn a successful discovery into an
        // error — the connection listener falls back to sensors-without-range.
        var discovered: EnergySensors? = null
        fun finish(result: HADiscoveryResult) {
            if (resumed) return
            resumed = true
            runCatching { dispatcher.stop() }
            cont.resume(result)
        }

        // Custom auth_ok handler — kick off the energy/get_prefs request.
        dispatcher.registerHandler("auth_ok", object : MessageHandler<HAMessage> {
            override fun handleMessage(message: HAMessage) {
                dispatcher.isAuthorized = true
                val resultHandler = object : MessageHandler<EnergyPrefsResult> {
                    override fun handleMessage(m: HAMessage) {
                        val result = m as EnergyPrefsResult
                        if (!result.isSuccess) {
                            finish(HADiscoveryResult(null, "Home Assistant rejected the request"))
                            return
                        }
                        val solar = mutableListOf<String>()
                        val batteries = mutableListOf<BatterySensor>()
                        var gridFrom: List<String> = emptyList()
                        var gridTo: List<String> = emptyList()
                        for (src in result.result.energySources) {
                            when (src.type) {
                                "solar" -> src.statEnergyFrom?.let { solar.add(it) }
                                "battery" -> batteries.add(BatterySensor().apply {
                                    batteryCharging = src.statEnergyTo
                                    batteryDischarging = src.statEnergyFrom
                                })
                                "grid" -> {
                                    gridFrom = src.flowFrom.orEmpty().mapNotNull { it.statEnergyFrom }
                                    gridTo   = src.flowTo.orEmpty().mapNotNull { it.statEnergyTo }
                                }
                            }
                        }
                        val sensors = EnergySensors().apply {
                            solarGeneration = solar
                            this.batteries = batteries
                            gridImports = gridFrom
                            gridExports = gridTo
                            // "Individual devices" arrive with a name-based role suggestion;
                            // the caller merges any classification the user already made.
                            devices = result.result.deviceConsumption.orEmpty()
                                .mapNotNull { dc ->
                                    dc.statConsumption?.let { DeviceSensor(it, dc.name) }
                                }
                        }
                        discovered = sensors
                        // Same socket, still authenticated: ask the recorder how far
                        // back statistics go before closing (Discover / Re-discover
                        // also refreshes the "available on server" range).
                        val statIds = (gridFrom + gridTo + solar +
                                batteries.flatMap {
                                    listOfNotNull(it.batteryCharging, it.batteryDischarging)
                                }).distinct()
                        probeServerRange(dispatcher, statIds) { range ->
                            finish(HADiscoveryResult(sensors, null, range?.first, range?.second))
                        }
                    }
                    override fun getMessageClass(): Class<out HAMessage> =
                        EnergyPrefsResult::class.java
                }
                val request = EnergyPrefsRequest().apply { id = dispatcher.generateId() }
                dispatcher.sendMessage(request, resultHandler)
            }
            override fun getMessageClass(): Class<out HAMessage> = AuthOK::class.java
        })

        dispatcher.registerHandler("auth_invalid", object : MessageHandler<HAMessage> {
            override fun handleMessage(message: HAMessage) {
                dispatcher.isAuthorized = false
                finish(HADiscoveryResult(null, "Invalid credentials"))
            }
            override fun getMessageClass(): Class<out HAMessage> = AuthInvalid::class.java
        })

        // Terminal signal: a dropped socket resumes with an error instead of hanging the
        // caller — unless the sensors already parsed, in which case discovery succeeded
        // and only the best-effort range probe was cut short.
        dispatcher.setConnectionListener { reason ->
            val sensors = discovered
            if (sensors != null) finish(HADiscoveryResult(sensors, null))
            else finish(HADiscoveryResult(null, "Connection lost: $reason"))
        }

        cont.invokeOnCancellation { runCatching { dispatcher.stop() } }

        try {
            dispatcher.start()
        } catch (t: Throwable) {
            finish(HADiscoveryResult(null, t.message ?: "Connection failed"))
        }
    }

/**
 * Best-effort probe for the first/last day the HA recorder has statistics for
 * [statIds]. Three cheap `statistics_during_period` requests on the already-
 * authenticated socket: monthly buckets over the whole plausible history to
 * bracket the range, then daily buckets over the first and last months to pin
 * the exact days. Any failure (or no data at all) resolves to null.
 */
private fun probeServerRange(
    dispatcher: HADispatcher,
    statIds: List<String>,
    onDone: (Pair<LocalDate, LocalDate>?) -> Unit
) {
    if (statIds.isEmpty()) {
        onDone(null)
        return
    }
    val zone = ZoneId.systemDefault()
    fun toDay(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun query(start: LocalDateTime, end: LocalDateTime, monthly: Boolean,
              onBuckets: (List<Long>) -> Unit) {
        val request = StatsForPeriodRequest(statIds)
        if (monthly) request.setMonthPeriod() else request.setDayPeriod()
        request.setStartAndEndTimes(start, end, dispatcher.generateId())
        val handler = object : MessageHandler<StatsForPeriodResult> {
            override fun handleMessage(m: HAMessage) {
                val result = m as? StatsForPeriodResult
                if (result == null || !result.isSuccess) {
                    onBuckets(emptyList())
                    return
                }
                onBuckets(result.result.orEmpty().values.flatten().map { it.start })
            }
            override fun getMessageClass(): Class<out HAMessage> =
                StatsForPeriodResult::class.java
        }
        // send() failures surface via the connection listener; resolving the
        // callback empty here as well keeps the chain from stalling silently.
        runCatching { dispatcher.sendMessage(request, handler) }
            .onFailure { onBuckets(emptyList()) }
    }

    val now = LocalDateTime.now()
    // Long-term statistics only exist since HA 2021.8 — 2015 is a safely early floor.
    query(LocalDateTime.of(2015, 1, 1, 0, 0), now, monthly = true) { months ->
        if (months.isEmpty()) {
            onDone(null)
            return@query
        }
        val firstMonth = toDay(months.minOrNull()!!).withDayOfMonth(1)
        val lastMonth = toDay(months.maxOrNull()!!).withDayOfMonth(1)
        query(firstMonth.atStartOfDay(), firstMonth.plusMonths(1).atStartOfDay(),
            monthly = false) { firstDays ->
            val start = firstDays.minOrNull()?.let(::toDay) ?: firstMonth
            query(lastMonth.atStartOfDay(), now, monthly = false) { lastDays ->
                val end = lastDays.maxOrNull()?.let(::toDay) ?: lastMonth
                onDone(start to end)
            }
        }
    }
}
