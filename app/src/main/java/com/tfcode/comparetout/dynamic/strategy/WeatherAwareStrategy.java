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

import java.util.Collections;
import java.util.List;

/**
 * The Layer-B pre-positioning decorator: run the base strategy, then check
 * the outlook — if a coming day's confidence-weighted expected peak import
 * price beats anything today's export windows would earn, HOLD the charge
 * (drop today's discharge windows) instead of selling it. A kWh kept back
 * avoids buying at the spike; a kWh sold today earns less than that.
 *
 * <p>Charge windows are never touched — cheap slots stay cheap regardless of
 * what's coming — and with an empty outlook the decisions pass through
 * unchanged, so the decorator is a strict Layer-A superset.
 */
public final class WeatherAwareStrategy implements DispatchStrategy {

    private final DispatchStrategy base;

    public WeatherAwareStrategy(DispatchStrategy base) {
        this.base = base;
    }

    @Override
    public String name() {
        return base.name() + " ☁";
    }

    @Override
    public DayDecisions decideDay(DayContext ctx) {
        DayDecisions decisions = base.decideDay(ctx);
        if (ctx.outlook.isEmpty() || decisions.dischargeWindows.isEmpty()) return decisions;

        double bestSellToday = 0;
        for (DayDecisions.Window w : decisions.dischargeWindows) {
            for (int slot = w.beginMinute / 30; slot < w.endMinute / 30; slot++) {
                bestSellToday = Math.max(bestSellToday, ctx.sell[slot]);
            }
        }
        double expectedSpike = 0;
        for (DayOutlook day : ctx.outlook) {
            double peak = 0;
            for (double price : day.expectedBuy) peak = Math.max(peak, price);
            expectedSpike = Math.max(expectedSpike, peak * day.confidence);
        }
        if (expectedSpike <= bestSellToday) return decisions;

        // Hold: keep the charge windows, drop the exports, and re-estimate
        // the end-of-day SOC from the slot masks (they are half-hour aligned
        // by construction — toWindows built them).
        boolean[] charge = toSlots(decisions.chargeWindows);
        boolean[] discharge = new boolean[48];
        double socEnd = SocForwardModel.socAtEndOfDay(ctx, charge, discharge);
        return new DayDecisions(decisions.chargeWindows, Collections.emptyList(), socEnd);
    }

    private static boolean[] toSlots(List<DayDecisions.Window> windows) {
        boolean[] slots = new boolean[48];
        for (DayDecisions.Window w : windows) {
            for (int slot = w.beginMinute / 30; slot < Math.min(48, w.endMinute / 30); slot++) {
                slots[slot] = true;
            }
        }
        return slots;
    }
}
