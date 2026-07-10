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

import java.util.List;

/**
 * The simplest dispatch rule: grid-charge whenever the import price is below
 * X, force-export whenever the export price is above Y.
 *
 * <p>A spread guard keeps Y honest: an export slot must also beat the day's
 * cheapest charge price grossed up for round-trip loss and degradation, plus
 * {@code minSpreadCents} — otherwise a mis-set Y cycles the battery at a
 * loss. Slots that qualify for both sides (deeply negative prices) charge
 * rather than discharge.
 */
public final class ThresholdStrategy implements DispatchStrategy {

    private final double chargeBelowCents;
    private final double dischargeAboveCents;
    private final double minSpreadCents;

    public ThresholdStrategy(double chargeBelowCents, double dischargeAboveCents,
                             double minSpreadCents) {
        this.chargeBelowCents = chargeBelowCents;
        this.dischargeAboveCents = dischargeAboveCents;
        this.minSpreadCents = minSpreadCents;
    }

    @Override
    public String name() {
        return "Threshold";
    }

    @Override
    public DayDecisions decideDay(DayContext ctx) {
        boolean[] charge = new boolean[48];
        boolean[] discharge = new boolean[48];

        double cheapestBuy = Double.MAX_VALUE;
        for (double b : ctx.buy) cheapestBuy = Math.min(cheapestBuy, b);
        double breakEven = ctx.battery.breakEvenSellCents(cheapestBuy) + minSpreadCents;

        for (int i = 0; i < 48; i++) {
            charge[i] = ctx.buy[i] < chargeBelowCents;
            discharge[i] = !charge[i]
                    && ctx.sell[i] > dischargeAboveCents
                    && ctx.sell[i] >= breakEven;
        }

        List<DayDecisions.Window> chargeWindows =
                DayDecisions.toWindows(charge, 100d, 0d);
        List<DayDecisions.Window> dischargeWindows =
                DayDecisions.toWindows(discharge, ctx.battery.floorPercent, exportRateKw(ctx.battery));
        double socEnd = SocForwardModel.socAtEndOfDay(ctx, charge, discharge);
        return new DayDecisions(chargeWindows, dischargeWindows, socEnd);
    }

    /** The battery's sustained discharge rate in kW, capped by the export limit. */
    static double exportRateKw(BatterySpec spec) {
        double batteryKw = spec.dischargeKwhPerHalfHour * 2d;
        return spec.exportCapKw > 0 ? Math.min(batteryKw, spec.exportCapKw) : batteryKw;
    }
}
