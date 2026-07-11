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
 * Layer-B foresight for one future day (~D+2..D+10): expected import prices
 * from the wind→price model, with a confidence that shrinks as the horizon
 * grows. Strategies weight what they read by the confidence — a low-wind
 * price spike ten days out is a hint, the same spike the day after tomorrow
 * is nearly firm.
 */
public final class DayOutlook {

    /** The future day, on the 2001 sim calendar. */
    public final LocalDate date;
    /** Expected import price per half-hour, c/kWh (48 slots). */
    public final double[] expectedBuy;
    /** 0..1; product of the horizon decay and any calibration haircut. */
    public final double confidence;

    public DayOutlook(LocalDate date, double[] expectedBuy, double confidence) {
        this.date = date;
        this.expectedBuy = expectedBuy;
        this.confidence = confidence;
    }
}
