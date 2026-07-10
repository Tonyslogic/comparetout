/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.ui2

import android.content.Context
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.dynamic.strategy.BatterySpec
import com.tfcode.comparetout.dynamic.strategy.DispatchStrategy
import com.tfcode.comparetout.dynamic.strategy.ScheduleEmitter
import com.tfcode.comparetout.dynamic.strategy.StrategyYearRunner
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.util.RateLookup
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a [DispatchStrategy] over a materialised dynamic plan's prices and
 * writes the resulting LoadShift/DischargeToGrid rows into a **generated
 * scenario** — a copy of the base scenario named
 * `"<base> ⚡ <strategy> (<plan year>)"`. The name is the provenance and the
 * lifecycle: regenerating clobbers the same-named scenario; deleting it in
 * the scenario list removes it like any other.
 *
 * The copy shares the base's panels and load profile (junction-row reuse —
 * their fetched data comes along, so the new scenario simulates without
 * refetching); every other component is duplicated. The base's own
 * LoadShift/DischargeToGrid rows are REPLACED by the strategy's — the point
 * of the generated scenario is that the strategy owns the battery schedule.
 *
 * All methods are synchronous — call on Dispatchers.IO.
 */
@Singleton
class StrategyScenarioGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ToutcRepository
) {

    sealed class Result {
        data class Generated(
            val scenarioName: String,
            val chargeRows: Int,
            val dischargeRows: Int
        ) : Result()

        data class Failed(val reason: String) : Result()
    }

    fun generateBlocking(baseScenarioId: Long, planId: Long, strategy: DispatchStrategy): Result {
        val components = repository.getScenarioComponentsForScenarioID(baseScenarioId)
            ?: return Result.Failed("Scenario not found")
        val base = components.scenario ?: return Result.Failed("Scenario not found")
        val inverters = components.inverters.orEmpty()
        val batteries = components.batteries.orEmpty()
        val connected = batteries.filter { b -> inverters.any { it.inverterName == b.inverter } }
        if (connected.isEmpty())
            return Result.Failed("The base scenario needs an inverter-connected battery")
        val loadProfile = components.loadProfile
            ?: return Result.Failed("The base scenario has no load profile")

        val plan = repository.getAllPricePlansNow().firstOrNull { it.pricePlanIndex == planId }
            ?: return Result.Failed("Price plan not found")
        val rates = repository.getAllDayRatesForPricePlanID(planId)
        val buyRates = DayRate.buyRates(rates)
        if (buyRates.isEmpty())
            return Result.Failed("The plan has no materialised prices yet — generate them first")
        val sellRates = DayRate.sellRates(rates)
        val buyLookup = RateLookup(plan, buyRates)
        val sellLookup = if (sellRates.isEmpty()) null else RateLookup(plan, sellRates)

        val batteryInverters = inverters.filter { inv ->
            connected.any { it.inverter == inv.inverterName }
        }
        val referenceInverter = batteryInverters.first()
        val spec = BatterySpec(
            connected.sumOf { it.batterySize },
            connected.maxOf { it.dischargeStop },
            connected.sumOf { it.maxCharge } * 6.0,     // kWh/5min → kWh/half-hour
            connected.sumOf { it.maxDischarge } * 6.0,
            (referenceInverter.ac2dcLoss + referenceInverter.dc2acLoss).toDouble(),
            loadProfile.gridExportMax,
            0.0
        )

        val buyProvider = StrategyYearRunner.HalfHourlyProvider { date ->
            DoubleArray(48) { slot ->
                buyLookup.getRate(date.dayOfYear, slot * 30, costingDow(date), 0.0)
            }
        }
        val sellProvider = StrategyYearRunner.HalfHourlyProvider { date ->
            DoubleArray(48) { slot ->
                sellLookup?.getRate(date.dayOfYear, slot * 30, costingDow(date), 0.0) ?: plan.feed
            }
        }
        val loadProvider = StrategyYearRunner.HalfHourlyProvider { date ->
            DoubleArray(48) { slot -> halfHourLoadKwh(loadProfile, date, slot / 2) }
        }

        val decisions = StrategyYearRunner.run(strategy, spec, buyProvider, sellProvider, loadProvider)
        val dischargeRates = batteryInverters.associate { inv ->
            inv.inverterName to connected.first { it.inverter == inv.inverterName }.maxDischarge * 12.0
        }
        val emitted = ScheduleEmitter.emit(
            decisions, strategy.name(), referenceInverter.inverterName, dischargeRates)

        val yearSuffix = plan.dynamicTerms?.year?.let { " ($it)" } ?: ""
        val generatedName = "${base.scenarioName} ⚡ ${strategy.name()}$yearSuffix"

        // Rebuild the components as a new scenario: zeroed indices insert
        // copies; panels/loadProfile keep theirs and are shared via junction
        // rows (their data comes along). Capability flags for the replaced
        // schedule lists are reset — the insert only ever sets them true.
        base.scenarioName = generatedName
        base.scenarioIndex = 0
        base.isHasLoadShifts = false
        base.isHasDischarges = false
        components.inverters?.forEach { it.inverterIndex = 0 }
        components.batteries?.forEach { it.batteryIndex = 0 }
        components.heatPumps?.forEach { it.heatPumpIndex = 0 }
        components.evCharges?.forEach { it.evChargeIndex = 0 }
        components.evDiverts?.forEach { it.evDivertIndex = 0 }
        components.hwSchedules?.forEach { it.hwScheduleIndex = 0 }
        components.hwSystem?.hwSystemIndex = 0
        components.hwDivert?.hwDivertIndex = 0
        components.loadShifts = emitted.loadShifts
        components.discharges = emitted.discharges

        val newId = repository.insertScenarioAndReturnID(components, true)
        if (newId == 0L) return Result.Failed("Could not create scenario \"$generatedName\"")
        SimulatorLauncher.simulateIfNeeded(context)
        return Result.Generated(generatedName, emitted.loadShifts.size, emitted.discharges.size)
    }

    companion object {

        /** Costing's day-of-week convention: Sunday = 0 (see CostingWorker). */
        private fun costingDow(date: LocalDate): Int {
            val dow = date.dayOfWeek.value
            return if (dow == 7) 0 else dow
        }

        /**
         * The load-profile distribution estimate for one half-hour, mirroring
         * GenerateMissingLoadDataWorker.genLoad (annual → month → day-of-week →
         * hour), halved for the half-hour granularity.
         */
        fun halfHourLoadKwh(loadProfile: LoadProfile, date: LocalDate, hourOfDay: Int): Double {
            val distMonth = loadProfile.monthlyDist.monthlyDist[date.monthValue - 1] / 100.0
            val dowIdx = date.dayOfWeek.value.let { if (it == 7) 0 else it }
            val distDow = loadProfile.dowDist.dowDist[dowIdx] / 100.0
            val distHod = loadProfile.hourlyDist.dist[hourOfDay] / 100.0
            val occurrences = countDayOccurrenceInMonth(date.dayOfWeek, YearMonth.from(date))
            val monthUse = loadProfile.annualUsage * distMonth
            val dayUse = monthUse / occurrences * distDow
            return dayUse * distHod / 2.0
        }

        private fun countDayOccurrenceInMonth(dow: DayOfWeek, month: YearMonth): Int {
            var count = 0
            var d = month.atDay(1)
            while (!d.isAfter(month.atEndOfMonth())) {
                if (d.dayOfWeek == dow) count++
                d = d.plusDays(1)
            }
            return count
        }
    }
}
