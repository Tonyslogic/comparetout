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

package com.tfcode.comparetout.model.priceplan;

/**
 * Supplier terms for a dynamic (wholesale-tracking) tariff.
 * <p>
 * A dynamic plan is defined by these terms, not by its DayRates: the 365
 * single-day rates are a derived artefact, materialised from a fetched
 * historical wholesale series by applying
 * {@code clamp(wholesale * multiplier + adder, floor, cap)} per half-hour
 * (the sell side analogously via feedMultiplier/feedAdder). The terms are
 * therefore the canonical shareable form — a plan carrying terms but no BUY
 * rates is a valid "pending" plan awaiting materialisation.
 * <p>
 * Stored on PricePlan as a JSON TEXT column (same TypeConverter pattern as
 * Restrictions); null for every non-dynamic plan.
 */
public class DynamicTerms {
    /** Market descriptor id, e.g. "ISEM-DAM"; resolved via the region's market registry. */
    private String market;
    /** First calendar year of the materialised backtest window; null until materialised
     *  (a supplier offer is not year-bound — the window is the importer's choice). */
    private Integer year;
    /** First month (1-12) of the 12-month backtest window; null == 1 (a legacy
     *  calendar-year plan: Jan..Dec of {@link #year}). A rolling window such as
     *  Jul {@code year} .. Jun {@code year+1} sits inside the market's freshest
     *  ~12-month publication window, avoiding the perpetual calendar-year gap. */
    private Integer periodStartMonth;
    /** First day-of-month (1-31) of the window; null == 1. Lets an auto window
     *  start mid-month so a full year of coverage anchors to the market's actual
     *  latest published day rather than snapping to a month boundary. */
    private Integer periodStartDay;
    /** When true, the window is (re)derived from the market's latest available
     *  data at each materialisation — a full year ending at whatever the source
     *  currently publishes — and {@link #year}/{@link #periodStartMonth}/
     *  {@link #periodStartDay} record the resolved window. Null/false = a fixed
     *  window the user chose. */
    private Boolean autoWindow;
    /** Retail multiplier on the wholesale price (margin / losses / VAT-inclusive factor). */
    private Double multiplier;
    /** c/kWh added after the multiplier (network charges, supplier margin). */
    private Double adder;
    /** Optional max unit price, c/kWh ("capped at X" offers); null = uncapped. */
    private Double cap;
    /** Optional min unit price (some offers floor at 0); null = none. */
    private Double floor;
    /** Sell-side transform; null means the scalar PricePlan.feed applies. */
    private Double feedMultiplier;
    private Double feedAdder;
    /** Provenance: dataset/report ids and fetch date (audit trail, like Panel provenance). */
    private String sourceRef;

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getPeriodStartMonth() {
        return periodStartMonth;
    }

    public void setPeriodStartMonth(Integer periodStartMonth) {
        this.periodStartMonth = periodStartMonth;
    }

    /** First month of the backtest window, defaulting a legacy null to January. */
    public int effectiveStartMonth() {
        return (null == periodStartMonth) ? 1 : periodStartMonth;
    }

    public Integer getPeriodStartDay() {
        return periodStartDay;
    }

    public void setPeriodStartDay(Integer periodStartDay) {
        this.periodStartDay = periodStartDay;
    }

    /** First day-of-month of the window, defaulting a legacy null to the 1st. */
    public int effectiveStartDay() {
        return (null == periodStartDay) ? 1 : periodStartDay;
    }

    public Boolean getAutoWindow() {
        return autoWindow;
    }

    public void setAutoWindow(Boolean autoWindow) {
        this.autoWindow = autoWindow;
    }

    /** Whether the window auto-tracks the market's latest available data. */
    public boolean isAutoWindow() {
        return Boolean.TRUE.equals(autoWindow);
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public Double getAdder() {
        return adder;
    }

    public void setAdder(Double adder) {
        this.adder = adder;
    }

    public Double getCap() {
        return cap;
    }

    public void setCap(Double cap) {
        this.cap = cap;
    }

    public Double getFloor() {
        return floor;
    }

    public void setFloor(Double floor) {
        this.floor = floor;
    }

    public Double getFeedMultiplier() {
        return feedMultiplier;
    }

    public void setFeedMultiplier(Double feedMultiplier) {
        this.feedMultiplier = feedMultiplier;
    }

    public Double getFeedAdder() {
        return feedAdder;
    }

    public void setFeedAdder(Double feedAdder) {
        this.feedAdder = feedAdder;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    /**
     * Whether the terms alone define a materialisable plan: a known market and the
     * buy-side transform. Year, cap/floor, feed terms and provenance are optional.
     */
    public boolean isComplete() {
        return !(null == market) && !market.isEmpty()
                && !(null == multiplier) && !(null == adder);
    }

    /** Apply the buy-side transform to a wholesale price (c/kWh in, c/kWh out). */
    public double unitPrice(double wholesale) {
        double price = wholesale * (null == multiplier ? 1.0 : multiplier)
                + (null == adder ? 0.0 : adder);
        if (!(null == floor) && price < floor) price = floor;
        if (!(null == cap) && price > cap) price = cap;
        return price;
    }
}
