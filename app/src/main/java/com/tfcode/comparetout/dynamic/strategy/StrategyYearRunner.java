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

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rolling day-ahead loop: walk the 2001 sim year, hand each day to the
 * strategy with firm prices for today and tomorrow (Layer A), and carry the
 * strategy's SOC estimate forward. Providers are seams so the Android
 * assembler can back them with a materialised plan's RateLookup and the base
 * scenario's load-profile distributions, while tests use canned arrays.
 */
public final class StrategyYearRunner {

    /** 48 half-hourly values for a 2001 date (c/kWh for prices, kWh for load). */
    public interface HalfHourlyProvider {
        double[] halfHourly(LocalDate date);
    }

    /** Layer-B foresight for a 2001 date; empty when uncalibrated. */
    public interface OutlookProvider {
        List<DayOutlook> outlookFor(LocalDate date);
    }

    private StrategyYearRunner() {
    }

    /** Layer-A-only run: no outlook. */
    public static Map<LocalDate, DayDecisions> run(DispatchStrategy strategy, BatterySpec spec,
                                                   HalfHourlyProvider buy, HalfHourlyProvider sell,
                                                   HalfHourlyProvider load) {
        return run(strategy, spec, buy, sell, load, date -> Collections.emptyList());
    }

    /**
     * Run the strategy over the whole sim year. The battery starts the year
     * at its floor SOC, matching the engine's first-interval state.
     *
     * @return one {@link DayDecisions} per 2001 date, in calendar order.
     */
    public static Map<LocalDate, DayDecisions> run(DispatchStrategy strategy, BatterySpec spec,
                                                   HalfHourlyProvider buy, HalfHourlyProvider sell,
                                                   HalfHourlyProvider load,
                                                   OutlookProvider outlook) {
        Map<LocalDate, DayDecisions> out = new LinkedHashMap<>();
        double soc = spec.floorKwh();
        LocalDate date = LocalDate.of(2001, 1, 1);
        while (date.getYear() == 2001) {
            LocalDate next = date.plusDays(1);
            double[] nextDayBuy = next.getYear() == 2001 ? buy.halfHourly(next) : null;
            DayContext ctx = new DayContext(date, buy.halfHourly(date), sell.halfHourly(date),
                    nextDayBuy, load.halfHourly(date), soc, spec, outlook.outlookFor(date));
            DayDecisions decisions = strategy.decideDay(ctx);
            out.put(date, decisions);
            soc = Math.max(spec.floorKwh(), Math.min(spec.capacityKwh, decisions.socEndKwh));
            date = next;
        }
        return out;
    }
}
