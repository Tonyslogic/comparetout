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

package com.tfcode.comparetout.model.json.priceplan;

import com.google.gson.annotations.SerializedName;

/**
 * JSON form of a dynamic tariff's supplier terms (see DynamicTerms).
 * <p>
 * When a plan file carries a "Dynamic" block, "Rates" becomes optional: a
 * terms-only file is a valid, importable plan pending the download of the
 * historical price series. Terms-only is also the canonical export form for
 * dynamic plans — smaller files, and shared files stay free of market data.
 * "Year" is optional: a supplier's offer is not year-bound; the backtest year
 * is chosen at materialisation time.
 */
public class DynamicTermsJson {

    @SerializedName("Market")
    public String market;

    @SerializedName("Year")
    public Integer year;

    /** First month (1-12) of the 12-month backtest window; null/absent == January
     *  (a legacy calendar-year plan). */
    @SerializedName("PeriodStartMonth")
    public Integer periodStartMonth;

    /** First day-of-month (1-31) of the window; null/absent == the 1st. */
    @SerializedName("PeriodStartDay")
    public Integer periodStartDay;

    /** When true, the window auto-tracks the market's latest available data; the
     *  Year/PeriodStartMonth/PeriodStartDay then record the last resolved window. */
    @SerializedName("AutoWindow")
    public Boolean autoWindow;

    @SerializedName("Multiplier")
    public Double multiplier;

    @SerializedName("Adder")
    public Double adder;

    @SerializedName("Cap")
    public Double cap;

    @SerializedName("Floor")
    public Double floor;

    @SerializedName("FeedMultiplier")
    public Double feedMultiplier;

    @SerializedName("FeedAdder")
    public Double feedAdder;

    @SerializedName("SourceRef")
    public String sourceRef;
}
