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
 * The lightweight SOC forward model shared by the Phase-5 strategies: a
 * 48-slot walk that mirrors the engine's behaviour coarsely — while grid
 * charging the load is served from the grid (CFG semantics), while forced
 * discharging the battery exports, otherwise the battery serves load down to
 * its floor. PV is deliberately ignored (a conservative estimate: solar only
 * leaves more energy in the battery than planned).
 */
final class SocForwardModel {

    private SocForwardModel() {
    }

    /** Walk the day and return the estimated SOC at midnight, kWh. */
    static double socAtEndOfDay(DayContext ctx, boolean[] chargeSlots, boolean[] dischargeSlots) {
        BatterySpec spec = ctx.battery;
        double floor = spec.floorKwh();
        double soc = Math.max(floor, Math.min(spec.capacityKwh, ctx.socStartKwh));
        for (int i = 0; i < 48; i++) {
            if (chargeSlots[i]) {
                soc = Math.min(spec.capacityKwh, soc + spec.chargeKwhPerHalfHour);
            } else if (dischargeSlots[i]) {
                soc = Math.max(floor, soc - spec.dischargeKwhPerHalfHour);
            } else {
                double available = Math.max(0, soc - floor);
                soc -= Math.min(ctx.loadKwh[i], Math.min(spec.dischargeKwhPerHalfHour, available));
            }
        }
        return soc;
    }
}
