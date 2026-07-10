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

/**
 * Everything a {@link DispatchStrategy} may look at when deciding one day.
 *
 * <p>Layer A foresight: firm prices for the day being decided plus (when
 * available) the following day — matching what day-ahead publication actually
 * gives a household. {@link #nextDayBuy} is null on the last day of the year.
 * Layer B (expected prices D+2..D+10 with confidence) is a later phase; the
 * context will grow a field for it so strategies need no signature change.
 *
 * <p>Dates run on the simulator's 2001 calendar; prices are the tariff's
 * retail c/kWh (buy already capped/floored by the plan terms, sell the raw
 * feed transform).
 */
public final class DayContext {

    /** The day being decided, on the 2001 sim calendar. */
    public final LocalDate date;
    /** Retail import price per half-hour, c/kWh (48 slots, slot 0 = 00:00). */
    public final double[] buy;
    /** Export price per half-hour, c/kWh (48 slots). */
    public final double[] sell;
    /** Tomorrow's import prices, or null when the year ends today. */
    public final double[] nextDayBuy;
    /** Estimated household load per half-hour, kWh (48 slots). */
    public final double[] loadKwh;
    /** Battery state of charge at 00:00, kWh. */
    public final double socStartKwh;
    /** The battery being planned for. */
    public final BatterySpec battery;

    public DayContext(LocalDate date, double[] buy, double[] sell, double[] nextDayBuy,
                      double[] loadKwh, double socStartKwh, BatterySpec battery) {
        this.date = date;
        this.buy = buy;
        this.sell = sell;
        this.nextDayBuy = nextDayBuy;
        this.loadKwh = loadKwh;
        this.socStartKwh = socStartKwh;
        this.battery = battery;
    }
}
