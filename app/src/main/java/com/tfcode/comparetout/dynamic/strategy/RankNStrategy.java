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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rank the day's half-hours: charge in the N cheapest (plus any
 * negative-priced slot — being paid to charge is always taken), discharge in
 * the M dearest export slots that clear the break-even bar (average charge
 * cost grossed up for round-trip loss, plus degradation and
 * {@code minSpreadCents}).
 *
 * <p>The discharge count is additionally limited by a SOC forecast: only as
 * many export slots as the day's energy budget (carried SOC above the floor
 * plus what the charge slots add, bounded by capacity) can actually supply.
 */
public final class RankNStrategy implements DispatchStrategy {

    private final int chargeSlotCount;
    private final int dischargeSlotCount;
    private final double minSpreadCents;

    public RankNStrategy(int chargeSlotCount, int dischargeSlotCount, double minSpreadCents) {
        this.chargeSlotCount = chargeSlotCount;
        this.dischargeSlotCount = dischargeSlotCount;
        this.minSpreadCents = minSpreadCents;
    }

    @Override
    public String name() {
        return "Rank-" + chargeSlotCount + "/" + dischargeSlotCount;
    }

    @Override
    public DayDecisions decideDay(DayContext ctx) {
        BatterySpec spec = ctx.battery;
        boolean[] charge = new boolean[48];
        boolean[] discharge = new boolean[48];

        List<Integer> byBuyAsc = slotIndices();
        byBuyAsc.sort(Comparator.comparingDouble(i -> ctx.buy[i]));
        int taken = 0;
        double chargeCostSum = 0;
        for (int slot : byBuyAsc) {
            boolean negative = ctx.buy[slot] < 0;
            if (taken >= chargeSlotCount && !negative) break;
            charge[slot] = true;
            chargeCostSum += ctx.buy[slot];
            taken++;
        }
        double avgChargeCost = taken > 0 ? chargeCostSum / taken : 0;

        // Energy budget for exporting: what's already stored above the floor
        // plus what today's charge slots add, bounded by usable capacity.
        double stored = Math.max(0, ctx.socStartKwh - spec.floorKwh());
        double added = taken * spec.chargeKwhPerHalfHour;
        double usable = Math.min(stored + added, spec.capacityKwh - spec.floorKwh());
        int budgetSlots = spec.dischargeKwhPerHalfHour > 0
                ? (int) Math.floor(usable / spec.dischargeKwhPerHalfHour) : 0;

        double breakEven = spec.breakEvenSellCents(avgChargeCost) + minSpreadCents;
        List<Integer> bySellDesc = slotIndices();
        bySellDesc.sort(Comparator.comparingDouble((Integer i) -> ctx.sell[i]).reversed());
        int dischargeTaken = 0;
        for (int slot : bySellDesc) {
            if (dischargeTaken >= Math.min(dischargeSlotCount, budgetSlots)) break;
            if (charge[slot]) continue;
            if (ctx.sell[slot] < breakEven || ctx.sell[slot] <= 0) break; // sorted — none further qualify
            discharge[slot] = true;
            dischargeTaken++;
        }

        List<DayDecisions.Window> chargeWindows =
                DayDecisions.toWindows(charge, 100d, 0d);
        List<DayDecisions.Window> dischargeWindows =
                DayDecisions.toWindows(discharge, spec.floorPercent, ThresholdStrategy.exportRateKw(spec));
        double socEnd = SocForwardModel.socAtEndOfDay(ctx, charge, discharge);
        return new DayDecisions(chargeWindows, dischargeWindows, socEnd);
    }

    private static List<Integer> slotIndices() {
        List<Integer> idx = new ArrayList<>(48);
        for (int i = 0; i < 48; i++) idx.add(i);
        return idx;
    }
}
