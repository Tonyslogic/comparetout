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
 * Field names were verified against a live plant on 2026-07-28 (Phase 0
 * report). Confirmed on that account: {@code productPower} (pv),
 * {@code usePower} (load), {@code chargePower}/{@code dischargePower},
 * {@code onGridPower} (feed) with totals {@code totalProductPower},
 * {@code totalUsePower}, {@code totalOnGridPower}, {@code totalBuyPower}.
 * Notably that plant has <b>no per-slot buy series</b> (only the
 * {@code totalBuyPower} scalar) and <b>no battery daily totals</b>. Series and
 * totals are still looked up through candidate lists so other account variants
 * (and any future Huawei rename) change only the lists here.
 *
 * Sign/pairing stays dynamic (Solis as-built) when BOTH direction curves are
 * present: grid and battery pairs are matched to the daily totals by
 * magnitude, not by trusting the field names' apparent direction. When only
 * one grid direction series is present (the confirmed live shape:
 * {@code onGridPower} feed, no buy series), that curve is assigned to the
 * total it matches by proximity and the other side is balance-derived. When a
 * battery has no daily totals to anchor magnitude against, the confirmed
 * {@code chargePower}/{@code dischargePower} names are trusted and each curve
 * is integrated directly (kW × 5-min ⇒ ÷12) rather than dropped.
 *
 * Grid fallback: when no explicit grid series exists, buy/feed are derived
 * per slot from the (already normalised) power balance
 * {@code net = load + charge − pv − discharge}. Load fallback: plants
 * without a power sensor report no usable {@code usePower} — store
 * {@code load = max(0, pv − feed + buy)} per slot instead.
 *
 * The live xAxis stamps carry the date ({@code "2026-07-27 00:00"}); the time
 * portion is taken (the oracle fixtures were bare {@code "HH:mm"}).
 *
 * Battery charge is stored signed (+ charging, − discharging) in the shared
 * {@code charge} column — the Home Assistant convention.
 */
public class FusionSolarDataMassager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double SLOTS_PER_HOUR = 12.0; // 5-minute slots

    // Candidate response names, confirmed-live name first (2026-07-28 Phase 0
    // report), oracle/other-variant candidates after — extend, don't replace.
    static final String[] PV_SERIES = {"productPower"};
    static final String[] LOAD_SERIES = {"usePower"};
    static final String[] CHARGE_SERIES = {"chargePower"};
    static final String[] DISCHARGE_SERIES = {"dischargePower"};
    static final String[] BUY_SERIES = {"buyPower", "purchasePower"}; // absent on the tested plant
    static final String[] FEED_SERIES = {"onGridPower", "ongridPower", "feedinPower"};
    static final String[] PV_TOTAL = {"totalProductPower"};
    static final String[] LOAD_TOTAL = {"totalUsePower"};
    static final String[] CHARGE_TOTAL = {"totalChargePower"};       // absent on the tested plant
    static final String[] DISCHARGE_TOTAL = {"totalDischargePower"}; // absent on the tested plant
    static final String[] BUY_TOTAL = {"totalBuyPower", "totalPurchasePower"};
    static final String[] FEED_TOTAL = {"totalOnGridPower", "totalOngridPower"};

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

        // Battery: pair by magnitude when both daily totals are present; when
        // they are absent (the tested plant omits them) there is nothing to
        // anchor magnitude against, so trust the confirmed chargePower /
        // dischargePower names — otherwise a discharge-heavy day would flip the
        // stored sign.
        boolean batAIsCharge = null != chargeTotal && null != dischargeTotal
                ? pairWithLarger(curveSums[BAT_A], curveSums[BAT_B], chargeTotal, dischargeTotal)
                : true;

        // Grid: BOTH direction series → magnitude pairing (unchanged). Exactly
        // ONE (the confirmed live shape: onGridPower feed, no buy series) → that
        // curve takes the total it matches by proximity, the other side is
        // balance-derived. NEITHER → both balance-derived.
        boolean buyPresent = null != buySeries;
        boolean feedPresent = null != feedSeries;
        boolean bothGrid = buyPresent && feedPresent;
        boolean gridAIsBuy = bothGrid
                && pairWithLarger(curveSums[GRID_A], curveSums[GRID_B], buyTotal, feedTotal);
        int oneGridCurve = bothGrid ? -1 : buyPresent ? GRID_A : feedPresent ? GRID_B : -1;
        boolean oneGridIsBuy = oneGridCurve >= 0
                && nearerFirst(curveSums[oneGridCurve], buyTotal, feedTotal);

        double pvScale = scale(curveSums[PV], pvTotal);
        double loadScale = scale(curveSums[LOAD], loadTotal);
        double chargeScale = scale(curveSums[batAIsCharge ? BAT_A : BAT_B], chargeTotal);
        double dischargeScale = scale(curveSums[batAIsCharge ? BAT_B : BAT_A], dischargeTotal);
        double buyScale, feedScale;
        if (bothGrid) {
            buyScale = scale(curveSums[gridAIsBuy ? GRID_A : GRID_B], buyTotal);
            feedScale = scale(curveSums[gridAIsBuy ? GRID_B : GRID_A], feedTotal);
        } else if (oneGridCurve >= 0) {
            buyScale = oneGridIsBuy ? scale(curveSums[oneGridCurve], buyTotal) : 0;
            feedScale = oneGridIsBuy ? 0 : scale(curveSums[oneGridCurve], feedTotal);
        } else {
            buyScale = 0;
            feedScale = 0;
        }

        boolean haveLoadCurve = curveSums[LOAD] > 0 && null != loadTotal && loadTotal > 0;

        for (Map.Entry<Long, double[]> entry : slots.entrySet()) {
            double[] values = entry.getValue();
            double pv = values[PV] * pvScale;
            double charge = values[batAIsCharge ? BAT_A : BAT_B] * chargeScale;
            double discharge = values[batAIsCharge ? BAT_B : BAT_A] * dischargeScale;
            double load = haveLoadCurve ? values[LOAD] * loadScale : 0;

            // §1.3 derivation on the normalised (kWh) values, for whichever
            // grid direction has no explicit series.
            double net = load + charge - pv - discharge;
            double buy, feed;
            if (bothGrid) {
                buy = values[gridAIsBuy ? GRID_A : GRID_B] * buyScale;
                feed = values[gridAIsBuy ? GRID_B : GRID_A] * feedScale;
            } else if (oneGridCurve >= 0 && haveLoadCurve) {
                if (oneGridIsBuy) {
                    buy = values[oneGridCurve] * buyScale;
                    feed = Math.max(0, -net);
                } else {
                    feed = values[oneGridCurve] * feedScale;
                    buy = Math.max(0, net);
                }
            } else if (oneGridCurve >= 0) {
                // One grid series but no load curve to derive the other side.
                buy = oneGridIsBuy ? values[oneGridCurve] * buyScale : 0;
                feed = oneGridIsBuy ? 0 : values[oneGridCurve] * feedScale;
            } else if (haveLoadCurve) {
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
        String hhmm = stamp.trim();
        // Live portal stamps carry the date ("2026-07-27 00:00"); the oracle
        // fixtures are bare "HH:mm". Take the time portion of either.
        int space = hhmm.lastIndexOf(' ');
        if (space >= 0) hhmm = hhmm.substring(space + 1);
        try {
            return LocalTime.parse(hhmm, MIN_FORMAT);
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

    /**
     * Slot multiplier that turns a curve into stored kWh. With a daily total
     * the curve is normalised to it (its shape, the portal's kWh); without one
     * — FusionSolar omits battery daily totals — the curve is integrated
     * directly (kW × 5-min ⇒ ÷12), which is what normalisation reduces to for
     * a gap-free curve anyway. Zero-sum curves stay zero.
     */
    static double scale(double curveSum, Double dailyTotal) {
        if (curveSum <= 0) return 0;
        if (null == dailyTotal || dailyTotal <= 0) return 1.0 / SLOTS_PER_HOUR;
        return dailyTotal / curveSum;
    }

    /** True when {@code curveSum} (as kWh) sits nearer the FIRST daily total. */
    static boolean nearerFirst(double curveSum, Double totalFirst, Double totalSecond) {
        double kwh = curveSum / SLOTS_PER_HOUR;
        double dFirst = null == totalFirst ? Double.MAX_VALUE : Math.abs(kwh - totalFirst);
        double dSecond = null == totalSecond ? Double.MAX_VALUE : Math.abs(kwh - totalSecond);
        return dFirst <= dSecond;
    }
}
