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

package com.tfcode.comparetout.importers.fusionsolar;

import com.tfcode.comparetout.importers.fusionsolar.responses.EnergyBalanceResponse;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns one day's FusionSolar {@code energy-balance} response into 5-minute
 * {@code AlphaESSTransformedData} rows, mirroring {@code SolisDataMassager}.
 *
 * The central trick (inherited from the legacy tout-compare fetcher): each
 * curve is only trusted for its SHAPE. Samples are bucketed into 5-minute
 * slots and every curve is rescaled so its day sum equals the response's own
 * daily total — the stored kWh match FusionSolar's accounting regardless of
 * gaps, and the absolute kW→kWh conversion cancels out.
 *
 * Field names come from the oracle project and are pending live verification
 * (Phase 0 report), so every series/total is looked up through candidate
 * name lists — a report showing different names changes only the lists here.
 * Sign/pairing stays dynamic (Solis as-built): the grid and battery
 * direction curves are paired with the daily totals by magnitude, never by
 * trusting the field names' apparent direction.
 *
 * Grid fallback: when no explicit grid series exists, buy/feed are derived
 * per slot from the (already normalised) power balance
 * {@code net = load + charge − pv − discharge}. Load fallback: plants
 * without a power sensor report no usable {@code usePower} — store
 * {@code load = max(0, pv − feed + buy)} per slot instead.
 *
 * Battery charge is stored signed (+ charging, − discharging) in the shared
 * {@code charge} column — the Home Assistant convention.
 */
public class FusionSolarDataMassager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // Candidate response names, most-expected first (oracle-derived; a Phase 0
    // tester report wins over these — extend, don't replace, on drift).
    static final String[] PV_SERIES = {"productPower"};
    static final String[] LOAD_SERIES = {"usePower"};
    static final String[] CHARGE_SERIES = {"chargePower"};
    static final String[] DISCHARGE_SERIES = {"dischargePower"};
    static final String[] BUY_SERIES = {"buyPower", "purchasePower"};
    static final String[] FEED_SERIES = {"ongridPower", "onGridPower", "feedinPower"};
    static final String[] PV_TOTAL = {"totalProductPower"};
    static final String[] LOAD_TOTAL = {"totalUsePower"};
    static final String[] CHARGE_TOTAL = {"totalChargePower"};
    static final String[] DISCHARGE_TOTAL = {"totalDischargePower"};
    static final String[] BUY_TOTAL = {"totalBuyPower", "totalPurchasePower"};
    static final String[] FEED_TOTAL = {"totalOngridPower", "totalOnGridPower"};

    // Curve indexes inside the per-slot double[].
    private static final int PV = 0;
    private static final int LOAD = 1;
    private static final int GRID_A = 2;   // the "buy"-named series
    private static final int GRID_B = 3;   // the "ongrid"-named series
    private static final int BAT_A = 4;    // the "charge"-named series
    private static final int BAT_B = 5;    // the "discharge"-named series
    private static final int CURVES = 6;

    /**
     * @param sysSn   the storage namespace, "FusionSolar-&lt;dn sans NE=&gt;"
     * @param day     the fetched day (the plant's local day)
     * @param zone    the saved zone ({@code UserTimezoneStore.resolvedZone})
     * @param balance the energy-balance response for {@code day}
     * @return one row per 5-minute slot of the day (288, or 276/300 on DST
     *         transition days); empty when there are no usable samples (the
     *         plant was offline all day — store nothing so a later run
     *         re-fetches the day)
     */
    public static List<AlphaESSTransformedData> massage(
            String sysSn, LocalDate day, ZoneId zone, EnergyBalanceResponse balance) {

        List<AlphaESSTransformedData> rows = new ArrayList<>();
        if (null == balance) return rows;
        List<String> xAxis = balance.xAxis();
        if (xAxis.isEmpty()) return rows;

        Double[] pvSeries = balance.series(PV_SERIES);
        Double[] loadSeries = balance.series(LOAD_SERIES);
        Double[] chargeSeries = balance.series(CHARGE_SERIES);
        Double[] dischargeSeries = balance.series(DISCHARGE_SERIES);
        Double[] buySeries = balance.series(BUY_SERIES);
        Double[] feedSeries = balance.series(FEED_SERIES);
        boolean gridExplicit = null != buySeries || null != feedSeries;

        // 1. Bucket by xAxis stamp interpreted in the saved zone. A DST gap
        //    stamp resolves forward; fall-back duplicates land in the first
        //    occurrence and are averaged — normalisation absorbs both.
        Map<Long, double[]> sums = new TreeMap<>();
        Map<Long, Integer> counts = new TreeMap<>();
        boolean anySample = false;
        for (int i = 0; i < xAxis.size(); i++) {
            LocalTime stamp = parseStamp(xAxis.get(i));
            if (null == stamp) continue;
            long slot = ZonedDateTime.of(day, stamp, zone).toInstant().toEpochMilli();
            double[] slotSums = sums.computeIfAbsent(slot, k -> new double[CURVES]);
            anySample |= accumulate(slotSums, PV, pvSeries, i);
            anySample |= accumulate(slotSums, LOAD, loadSeries, i);
            anySample |= accumulate(slotSums, BAT_A, chargeSeries, i);
            anySample |= accumulate(slotSums, BAT_B, dischargeSeries, i);
            anySample |= accumulate(slotSums, GRID_A, buySeries, i);
            anySample |= accumulate(slotSums, GRID_B, feedSeries, i);
            counts.merge(slot, 1, Integer::sum);
        }
        if (!anySample) return rows;

        // 2. Every slot of the day, missing ones filled with zeros. The slot
        //    walk is zone-aware so DST days naturally get 276/300 rows.
        Map<Long, double[]> slots = new TreeMap<>();
        ZonedDateTime cursor = day.atStartOfDay(zone);
        ZonedDateTime dayEnd = day.plusDays(1).atStartOfDay(zone);
        while (cursor.isBefore(dayEnd)) {
            long key = cursor.toInstant().toEpochMilli();
            double[] slotSums = sums.get(key);
            double[] averaged = new double[CURVES];
            if (null != slotSums) {
                int n = counts.get(key);
                for (int i = 0; i < CURVES; i++) averaged[i] = slotSums[i] / n;
            }
            slots.put(key, averaged);
            cursor = cursor.plusMinutes(5);
        }

        // 3./4. Normalise each curve to its daily total; grid and battery
        //    direction pairs are matched larger-total ↔ larger-curve-sum.
        double[] curveSums = new double[CURVES];
        for (double[] values : slots.values())
            for (int i = 0; i < CURVES; i++) curveSums[i] += values[i];

        Double pvTotal = balance.scalar(PV_TOTAL);
        Double loadTotal = balance.scalar(LOAD_TOTAL);
        Double chargeTotal = balance.scalar(CHARGE_TOTAL);
        Double dischargeTotal = balance.scalar(DISCHARGE_TOTAL);
        Double buyTotal = balance.scalar(BUY_TOTAL);
        Double feedTotal = balance.scalar(FEED_TOTAL);

        boolean gridAIsBuy = pairWithLarger(curveSums[GRID_A], curveSums[GRID_B],
                buyTotal, feedTotal);
        boolean batAIsCharge = pairWithLarger(curveSums[BAT_A], curveSums[BAT_B],
                chargeTotal, dischargeTotal);

        double pvScale = scale(curveSums[PV], pvTotal);
        double loadScale = scale(curveSums[LOAD], loadTotal);
        double buyScale = scale(curveSums[gridAIsBuy ? GRID_A : GRID_B], buyTotal);
        double feedScale = scale(curveSums[gridAIsBuy ? GRID_B : GRID_A], feedTotal);
        double chargeScale = scale(curveSums[batAIsCharge ? BAT_A : BAT_B], chargeTotal);
        double dischargeScale = scale(curveSums[batAIsCharge ? BAT_B : BAT_A], dischargeTotal);

        boolean haveLoadCurve = curveSums[LOAD] > 0 && null != loadTotal && loadTotal > 0;

        for (Map.Entry<Long, double[]> entry : slots.entrySet()) {
            double[] values = entry.getValue();
            double pv = values[PV] * pvScale;
            double charge = values[batAIsCharge ? BAT_A : BAT_B] * chargeScale;
            double discharge = values[batAIsCharge ? BAT_B : BAT_A] * dischargeScale;
            double load = haveLoadCurve ? values[LOAD] * loadScale : 0;

            double buy, feed;
            if (gridExplicit) {
                buy = values[gridAIsBuy ? GRID_A : GRID_B] * buyScale;
                feed = values[gridAIsBuy ? GRID_B : GRID_A] * feedScale;
            } else if (haveLoadCurve) {
                // §1.3 derivation on the normalised (kWh) values.
                double net = load + charge - pv - discharge;
                buy = Math.max(0, net);
                feed = Math.max(0, -net);
            } else {
                // No grid series AND no load curve: nothing to balance
                // against — grid stays zero, load degenerates to pv below.
                buy = 0;
                feed = 0;
            }
            if (!haveLoadCurve) load = Math.max(0, pv - feed + buy);

            ZonedDateTime local = java.time.Instant.ofEpochMilli(entry.getKey()).atZone(zone);
            AlphaESSTransformedData row = new AlphaESSTransformedData();
            row.setSysSn(sysSn);
            row.setDate(local.format(DATE_FORMAT));
            row.setMinute(local.format(MIN_FORMAT));
            row.setMillisSinceEpoch(entry.getKey());
            row.setPv(pv);
            row.setLoad(load);
            row.setBuy(buy);
            row.setFeed(feed);
            row.setCharge(charge - discharge);
            rows.add(row);
        }
        return rows;
    }

    /** "NE=33554678" → "FusionSolar-33554678" (raw dn stays in the station list JSON). */
    public static String sysSnFor(String dn) {
        String normalised = null == dn ? "" : dn.trim();
        if (normalised.startsWith("NE=")) normalised = normalised.substring(3);
        return "FusionSolar-" + normalised;
    }

    /** Adds series[i] into the slot; true when a real (non-"--") sample was present. */
    private static boolean accumulate(double[] slotSums, int curve, Double[] series, int i) {
        if (null == series || i >= series.length || null == series[i]) return false;
        slotSums[curve] += series[i];
        return true;
    }

    private static LocalTime parseStamp(String stamp) {
        if (null == stamp) return null;
        try {
            return LocalTime.parse(stamp, MIN_FORMAT);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Pairs the direction curves with the daily totals: true when curve A
     * belongs with the FIRST total. Larger daily total ↔ larger curve sum;
     * ties (typically both zero) keep A↔first.
     */
    static boolean pairWithLarger(double sumA, double sumB, Double totalFirst, Double totalSecond) {
        double first = null == totalFirst ? 0 : totalFirst;
        double second = null == totalSecond ? 0 : totalSecond;
        return (sumA >= sumB) == (first >= second);
    }

    /** dailyTotal / curveSum; zero-sum or untrusted-total curves stay zero. */
    static double scale(double curveSum, Double dailyTotal) {
        if (curveSum <= 0 || null == dailyTotal || dailyTotal <= 0) return 0;
        return dailyTotal / curveSum;
    }
}
