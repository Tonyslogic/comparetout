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

package com.tfcode.comparetout.importers.solis;

import com.tfcode.comparetout.importers.solis.responses.StationDayEnergyResponse;
import com.tfcode.comparetout.importers.solis.responses.StationDayResponse;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns one day of SolisCloud station data (the ~5-minute {@code stationDay}
 * power curve + the {@code stationDayEnergyList} daily totals) into 5-minute
 * {@code AlphaESSTransformedData} rows.
 *
 * The central trick (inherited from the legacy tout-compare fetcher): each
 * curve is only trusted for its SHAPE. Samples are bucketed into 5-minute
 * slots and every curve is rescaled so its day sum equals SolisCloud's own
 * daily energy total — so the stored kWh match the portal's accounting
 * regardless of sample gaps, and the kW→kWh conversion cancels out.
 *
 * Sign conventions for grid ({@code psum}) and battery ({@code batteryPower})
 * are not documented, so no orientation is assumed: the two direction curves
 * (Zheng/Fu split when populated, else the sample's sign) are PAIRED with the
 * two daily totals by matching the larger curve sum with the larger total.
 * A mismatch is self-correcting per day and shows up immediately as a
 * portal-vs-dashboard kWh difference (verification step §3.4 of the plan).
 *
 * Battery charge is stored signed (+ charging, − discharging) in the shared
 * {@code charge} column — the Home Assistant convention.
 */
public class SolisDataMassager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long SLOT_MILLIS = 5 * 60 * 1000L;

    // Curve indexes inside the per-slot double[].
    private static final int PV = 0;
    private static final int LOAD = 1;
    private static final int GRID_A = 2;   // psum positive direction ("Zheng")
    private static final int GRID_B = 3;   // psum negative direction ("Fu")
    private static final int BAT_A = 4;    // batteryPower positive direction
    private static final int BAT_B = 5;    // batteryPower negative direction
    private static final int CURVES = 6;

    /**
     * @param sysSn   the storage namespace, "Solis-&lt;stationId&gt;"
     * @param day     the fetched day (the station's local day)
     * @param zone    the saved zone ({@code UserTimezoneStore.resolvedZone})
     * @param samples the stationDay power curve (irregular ~5-min stamps)
     * @param totals  the station's stationDayEnergyList record for {@code day}
     * @return one row per 5-minute slot of the day (288, or 276/300 on DST
     *         transition days); empty when there are no usable samples
     */
    public static List<AlphaESSTransformedData> massage(
            String sysSn, LocalDate day, ZoneId zone,
            List<StationDayResponse> samples,
            StationDayEnergyResponse.Record totals) {

        List<AlphaESSTransformedData> rows = new ArrayList<>();
        if (null == samples || samples.isEmpty() || null == totals) return rows;

        // 1. Bucket into 5-minute slots (floor of the UTC millis — the same
        //    zone-independent snapping DataMassager uses for AlphaESS), then
        //    average samples sharing a slot.
        Map<Long, double[]> sums = new TreeMap<>();
        Map<Long, Integer> counts = new TreeMap<>();
        for (StationDayResponse sample : samples) {
            if (null == sample || null == sample.time) continue;
            double[] curves = curvesFor(sample);
            if (null == curves) continue; // unknown unit — skip the sample
            long slot = sample.time / SLOT_MILLIS * SLOT_MILLIS;
            double[] slotSums = sums.computeIfAbsent(slot, k -> new double[CURVES]);
            for (int i = 0; i < CURVES; i++) slotSums[i] += curves[i];
            counts.merge(slot, 1, Integer::sum);
        }
        if (sums.isEmpty()) return rows;

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

        // 3./4. Normalise each curve to its daily total; the grid and battery
        //    direction pairs are matched larger-total ↔ larger-curve-sum.
        double[] curveSums = new double[CURVES];
        for (double[] values : slots.values())
            for (int i = 0; i < CURVES; i++) curveSums[i] += values[i];

        Double pvTotal = energyKwh(totals.energy, totals.energyStr);
        Double buyTotal = energyKwh(totals.gridPurchasedEnergy, totals.gridPurchasedEnergyStr);
        Double sellTotal = energyKwh(totals.gridSellEnergy, totals.gridSellEnergyStr);
        Double loadTotal = energyKwh(totals.homeLoadEnergy, totals.homeLoadEnergyStr);
        Double chargeTotal = energyKwh(totals.batteryChargeEnergy, totals.batteryChargeEnergyStr);
        Double dischargeTotal = energyKwh(totals.batteryDischargeEnergy, totals.batteryDischargeEnergyStr);

        boolean gridAIsBuy = pairWithLarger(curveSums[GRID_A], curveSums[GRID_B],
                buyTotal, sellTotal);
        boolean batAIsCharge = pairWithLarger(curveSums[BAT_A], curveSums[BAT_B],
                chargeTotal, dischargeTotal);

        double pvScale = scale(curveSums[PV], pvTotal);
        double loadScale = scale(curveSums[LOAD], loadTotal);
        double buyScale = scale(curveSums[gridAIsBuy ? GRID_A : GRID_B], buyTotal);
        double sellScale = scale(curveSums[gridAIsBuy ? GRID_B : GRID_A], sellTotal);
        double chargeScale = scale(curveSums[batAIsCharge ? BAT_A : BAT_B], chargeTotal);
        double dischargeScale = scale(curveSums[batAIsCharge ? BAT_B : BAT_A], dischargeTotal);

        boolean haveLoadCurve = curveSums[LOAD] > 0 && null != loadTotal && loadTotal > 0;

        for (Map.Entry<Long, double[]> entry : slots.entrySet()) {
            double[] values = entry.getValue();
            double pv = values[PV] * pvScale;
            double buy = values[gridAIsBuy ? GRID_A : GRID_B] * buyScale;
            double feed = values[gridAIsBuy ? GRID_B : GRID_A] * sellScale;
            double charge = values[batAIsCharge ? BAT_A : BAT_B] * chargeScale;
            double discharge = values[batAIsCharge ? BAT_B : BAT_A] * dischargeScale;
            // 5. Load: the metered curve when the station reports one; else a
            //    grid-type plant's energy balance (never negative), which
            //    degenerates to pv-only when grid figures are also absent.
            double load = haveLoadCurve
                    ? values[LOAD] * loadScale
                    : Math.max(0, pv - feed + buy);

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

    /**
     * The six direction-split kW curves for one sample, or null when a unit
     * is unrecognised (the sample is skipped rather than guessed at). The kW
     * conversion cancels in the normalisation but keeps mixed-unit days
     * consistent.
     */
    private static double[] curvesFor(StationDayResponse sample) {
        Double pv = powerKw(sample.power, sample.powerStr);
        Double load = powerKw(sample.familyLoadPower, sample.familyLoadPowerStr);
        if (null == pv || null == load) return null;

        double[] curves = new double[CURVES];
        curves[PV] = pv;
        curves[LOAD] = load;

        // Grid: prefer the explicit direction split, else the sign of psum.
        if (null != sample.psumZheng || null != sample.psumFu) {
            Double a = powerKw(sample.psumZheng, sample.psumStr);
            Double b = powerKw(sample.psumFu, sample.psumStr);
            if (null == a || null == b) return null;
            curves[GRID_A] = a;
            curves[GRID_B] = b;
        } else {
            Double psum = powerKw(sample.psum, sample.psumStr);
            if (null == psum) return null;
            curves[GRID_A] = Math.max(0, psum);
            curves[GRID_B] = Math.max(0, -psum);
        }

        // Battery: same treatment.
        if (null != sample.batteryPowerZheng || null != sample.batteryPowerFu) {
            Double a = powerKw(sample.batteryPowerZheng, sample.batteryPowerStr);
            Double b = powerKw(sample.batteryPowerFu, sample.batteryPowerStr);
            if (null == a || null == b) return null;
            curves[BAT_A] = a;
            curves[BAT_B] = b;
        } else {
            Double battery = powerKw(sample.batteryPower, sample.batteryPowerStr);
            if (null == battery) return null;
            curves[BAT_A] = Math.max(0, battery);
            curves[BAT_B] = Math.max(0, -battery);
        }
        return curves;
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

    /** Power to kW, or null for an unrecognised unit. Absent unit ⇒ kW (the observed default). */
    static Double powerKw(Double value, String unit) {
        if (null == value) return 0D; // absent field on a sample = no flow
        if (null == unit || unit.isEmpty() || "kW".equals(unit)) return value;
        if ("W".equals(unit)) return value * 0.001;
        if ("MW".equals(unit)) return value * 1000;
        return null;
    }

    /** Energy to kWh, or null for an unrecognised/absent value (curve stays zero). */
    static Double energyKwh(Double value, String unit) {
        if (null == value) return null;
        if (null == unit || unit.isEmpty() || "kWh".equals(unit)) return value;
        if ("Wh".equals(unit)) return value * 0.001;
        if ("MWh".equals(unit)) return value * 1000;
        return null;
    }
}
