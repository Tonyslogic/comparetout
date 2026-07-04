package com.tfcode.comparetout.ui2

import com.tfcode.comparetout.importers.homeassistant.BatterySensor
import com.tfcode.comparetout.importers.homeassistant.DeviceSensor
import com.tfcode.comparetout.importers.homeassistant.EnergySensors
import com.tfcode.comparetout.importers.homeassistant.HADispatcher
import com.tfcode.comparetout.importers.homeassistant.MessageHandler
import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsRequest
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK
import com.tfcode.comparetout.importers.homeassistant.messages.energyPrefsResult.EnergyPrefsResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// ──────────────────────────────────────────────────────────────────────────
// Kotlin coroutine wrapper around the legacy Java HADispatcher. Lets the
// UI2 data-source management screen perform credential validation + energy-
// sensor discovery without the Fragment lifecycle the legacy ImportHAOverview
// uses.
//
// The WebSocket lifecycle is short-lived: connect → auth → energy/get_prefs
// → parse → close. We just translate that into a suspending function whose
// result is a Pair<EnergySensors?, String?> (sensors-or-null, error-or-null).
// ──────────────────────────────────────────────────────────────────────────

/**
 * Result of a discovery attempt. Exactly one of [sensors] / [error] is set.
 * If authentication failed, [error] reads "Invalid credentials".
 */
data class HADiscoveryResult(val sensors: EnergySensors?, val error: String?)

/**
 * Connect to a Home Assistant WebSocket endpoint, authenticate with [token],
 * fetch the user's energy preferences, and return the parsed sensor set.
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
                        finish(HADiscoveryResult(sensors, null))
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

        // Terminal signal: a dropped socket resumes with an error instead of hanging the caller.
        dispatcher.setConnectionListener { reason ->
            finish(HADiscoveryResult(null, "Connection lost: $reason"))
        }

        cont.invokeOnCancellation { runCatching { dispatcher.stop() } }

        try {
            dispatcher.start()
        } catch (t: Throwable) {
            finish(HADiscoveryResult(null, t.message ?: "Connection failed"))
        }
    }
