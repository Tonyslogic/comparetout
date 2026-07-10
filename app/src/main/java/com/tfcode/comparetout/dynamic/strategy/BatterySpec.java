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

package com.tfcode.comparetout.dynamic.strategy;

/**
 * The battery parameters a dispatch strategy plans against. This is an
 * aggregate of the base scenario's inverter-connected batteries — the
 * strategy reasons about total storage; the emitter splits discharge rows
 * back out per inverter.
 *
 * <p>Rates are kWh per half-hour (the strategy's decision granularity), not
 * the entity's kWh-per-5-minute fields — the assembler converts
 * ({@code maxCharge * 6}).
 */
public final class BatterySpec {

    /** Total usable capacity, kWh. */
    public final double capacityKwh;
    /** SOC floor as a percentage of capacity (the dischargeStop). */
    public final double floorPercent;
    /** Max energy into the battery per half-hour, kWh. */
    public final double chargeKwhPerHalfHour;
    /** Max energy out of the battery per half-hour, kWh. */
    public final double dischargeKwhPerHalfHour;
    /** Round-trip loss, percent (charge + discharge conversion). */
    public final double roundTripLossPercent;
    /** Grid export cap, kW; 0 or negative means uncapped. */
    public final double exportCapKw;
    /** Optional throughput cost, cents per kWh cycled (default 0 = off). */
    public final double degradationCostPerKwh;

    public BatterySpec(double capacityKwh, double floorPercent,
                       double chargeKwhPerHalfHour, double dischargeKwhPerHalfHour,
                       double roundTripLossPercent, double exportCapKw,
                       double degradationCostPerKwh) {
        this.capacityKwh = capacityKwh;
        this.floorPercent = floorPercent;
        this.chargeKwhPerHalfHour = chargeKwhPerHalfHour;
        this.dischargeKwhPerHalfHour = dischargeKwhPerHalfHour;
        this.roundTripLossPercent = roundTripLossPercent;
        this.exportCapKw = exportCapKw;
        this.degradationCostPerKwh = degradationCostPerKwh;
    }

    /** The SOC floor in kWh. */
    public double floorKwh() {
        return (floorPercent / 100d) * capacityKwh;
    }

    /** Fraction of a stored kWh that comes back out (1 − loss). */
    public double roundTripEfficiency() {
        return Math.max(0.01, 1d - roundTripLossPercent / 100d);
    }

    /**
     * What a kWh bought at {@code buyCents} must fetch on export to break
     * even: the buy price grossed up for round-trip loss, plus degradation.
     */
    public double breakEvenSellCents(double buyCents) {
        return Math.max(0, buyCents) / roundTripEfficiency() + degradationCostPerKwh;
    }
}
