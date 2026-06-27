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

package com.tfcode.comparetout.scenario.sim;

import java.util.List;

/**
 * Pure heat-pump demand physics (Phase 1 of the heat-pump plan, {@code plans/hp/plan.md}; model in
 * {@code plans/hp/design.md} §3). No Android, no Room, no network — a deterministic calculator from a
 * fuel/HP/home description plus an outdoor-weather series to a per-interval electrical load
 * ({@code heatPumpLoad}).
 *
 * <p><b>Guiding principle:</b> anchor on the user's trusted headline numbers (annual fuel use → annual
 * delivered heat; seasonal SCOP) and let the weather only <i>redistribute</i> demand across time — physics
 * never changes an annual total. The five transforms:</p>
 * <ol>
 *   <li><b>Fuel → delivered heat:</b> {@code fuel × calorific × boilerEff}, minus domestic hot water
 *       (a fixed kWh default, or a space-heating fraction). This space-heat figure is the anchor.</li>
 *   <li><b>Setpoint scale:</b> if the user wants a different indoor temperature than today, scale by the
 *       degree-day ratio {@code HDD(setpointNew)/HDD(setpointOld)} (= 1 when unchanged).</li>
 *   <li><b>Redistribution:</b> spread the annual demand across intervals by
 *       {@code HDD × windFactor × hourlyProfile × dowProfile}, optionally gated to a heating season,
 *       then <b>renormalised</b> so the per-interval thermal demand sums back to the anchor.</li>
 *   <li><b>COP:</b> a temperature-dependent COP {@code copRated + slope·(T−refT)} whose <i>level</i> is
 *       calibrated so the realised seasonal COP equals the user's SCOP; electrical = thermal ÷ COP.</li>
 *   <li><b>Capacity clamp + backup:</b> the HP can only move {@code capacity·Δt} of heat per interval;
 *       any excess on the coldest intervals is met by a resistive backup heater (COP = 1), conserving the
 *       annual heat total.</li>
 * </ol>
 *
 * <p>The renormalisation (3) and the SCOP calibration (4) both need the <i>whole</i> series, so everything
 * is computed once in the constructor; {@link #loadForIndex(int)} is then an O(1) lookup. This mirrors how
 * the Phase-2 {@code HeatPumpComponent} will be constructed once per scenario run.</p>
 */
public final class HeatPumpDemandModel {

    /** One interval of the outdoor-weather series, in chronological order. */
    public static final class WeatherSample {
        public final double tempC;       // outdoor air temperature, Celsius
        public final double windSpeed;   // 10 m wind speed, m/s
        public final int hourOfDay;      // 0..23, for the hourly profile
        public final int dowIndex;       // 0..6 (Mon..Sun), for the day-of-week profile
        public final int dayOfYear;      // 1..366, for the heating-season window

        public WeatherSample(double tempC, double windSpeed, int hourOfDay, int dowIndex, int dayOfYear) {
            this.tempC = tempC;
            this.windSpeed = windSpeed;
            this.hourOfDay = hourOfDay;
            this.dowIndex = dowIndex;
            this.dayOfYear = dayOfYear;
        }
    }

    /** Configuration — all the user/derived parameters of the model, with sensible defaults. */
    public static final class Config {
        // (1) fuel → delivered heat
        public double fuelAnnual = 2300d;        // litres/yr (oil) or kWh/yr (gas)
        public double calorificValue = 10.35d;   // kWh per unit (kerosene ~10.35, LPG ~7.08, gas = 1.0)
        public double boilerEfficiency = 0.80d;  // old-boiler seasonal efficiency
        public double dhwAnnualKWh = 2000d;      // fixed DHW carve-out (basic mode)
        public Double spaceHeatingFraction = null; // advanced: if set, overrides the fixed DHW subtraction

        // (1b) fabric anchor (new build): when both are > 0 they replace the fuel anchor above, deriving the
        // annual space heat from the building fabric instead of a past fuel bill. HLC = area·HLI [W/K].
        public double floorAreaM2 = 0d;          // heated floor area (m²); 0 ⇒ use the fuel anchor
        public double heatLossIndex = 0d;        // whole-house HLI (W/K/m², fabric + ventilation); 0 ⇒ fuel anchor

        // (2) setpoint scale
        public double setpointNew = 20d;         // desired indoor temperature
        public double setpointOld = 20d;         // current indoor temperature (== new ⇒ no scaling)

        // (3) redistribution
        public double balancePoint = 15.5d;      // T_base: outdoor temp below which heating is needed
        public double alphaWind = 0.03d;         // wind-infiltration coefficient per m/s
        public double[] hourlyProfile = flat(24);// 24 weights (relative); flat by default
        public double[] dowProfile = flat(7);    // 7 weights (relative), Mon..Sun
        public Integer heatOnDayOfYear = null;   // heating-season window start (inclusive); null ⇒ year-round
        public Integer heatOffDayOfYear = null;  // heating-season window end (inclusive)

        // (4) COP
        public double copRated = 4.2d;           // rated COP at copRefTemp
        public double copRefTemp = 7d;           // reference outdoor temp for the rated COP (A7/W35)
        public double copSlope = 0.08d;          // COP change per +1 C outdoor (datasheet-typical)
        public double scop = 3.6d;               // seasonal COP the calibration pins the level to

        // (5) capacity
        public double capacityKw = 7d;           // max thermal output (kW)
        public boolean backupHeater = true;      // resistive top-up when capacity is exceeded

        // grid
        public double intervalHours = 1d;        // interval length in hours (1.0 hourly; 1/12 for 5-min grid)

        private static double[] flat(int n) {
            double[] a = new double[n];
            for (int i = 0; i < n; i++) a[i] = 1d;
            return a;
        }
    }

    private static final double COP_FLOOR = 1.0d;   // a heat pump never does worse than resistive heating

    private final List<WeatherSample> series; // retained for outdoor-temp / wind passthrough channels

    private final double[] thermalKWh;       // (3) per-interval thermal demand
    private final double[] cop;              // (4) per-interval calibrated COP
    private final double[] loadKWh;          // (5) per-interval total electrical load (heatPumpLoad)
    private final double[] backupKWh;        // (5) per-interval resistive backup electrical (subset of load)
    private final double[] heatDeliveredKWh; // (5) per-interval heat actually delivered (= demand, unless droop)

    private final double annualSpaceHeat;      // the anchor: Q_demand (kWh)
    private final double annualElectricity;    // Σ loadKWh (kWh, after clamp+backup)
    private final double calibratedElectricity;// Σ thermal/cop, before the capacity clamp (kWh)

    public HeatPumpDemandModel(Config cfg, List<WeatherSample> series) {
        int n = series.size();
        this.series = series;
        this.thermalKWh = new double[n];
        this.cop = new double[n];
        this.loadKWh = new double[n];
        this.backupKWh = new double[n];
        this.heatDeliveredKWh = new double[n];

        // (1) annual space heat (the trusted anchor) — either the fabric anchor (new build) or the fuel anchor.
        double spaceHeat;
        if (cfg.floorAreaM2 > 0d && cfg.heatLossIndex > 0d) {
            // Fabric anchor: HLC = area·HLI [W/K]; annual heat = HLC × HDD(base) × 24h ÷ 1000. The HDD is taken
            // from the actual weather at the balance point, so the headline total is consistent with the hourly
            // redistribution that follows. hddSum is Σmax(0, base−T) over the samples (= degree-hours / Δt), so
            // degree-days = hddSum·Δt/24 and annual heat = HLC·(hddSum·Δt/24)·24/1000 = HLC·hddSum·Δt/1000.
            spaceHeat = cfg.floorAreaM2 * cfg.heatLossIndex
                    * hddSum(series, cfg.balancePoint) * cfg.intervalHours / 1000d;
        } else {
            // Fuel anchor: fuel × calorific × boilerEff, minus domestic hot water (fixed kWh or a fraction).
            double deliveredGross = cfg.fuelAnnual * cfg.calorificValue * cfg.boilerEfficiency;
            spaceHeat = (cfg.spaceHeatingFraction != null)
                    ? deliveredGross * cfg.spaceHeatingFraction
                    : Math.max(0d, deliveredGross - cfg.dhwAnnualKWh);
        }

        // (2) setpoint scale: degree-day ratio between desired and current indoor temperature
        double hddNew = hddSum(series, cfg.setpointNew);
        double hddOld = hddSum(series, cfg.setpointOld);
        double setpointScale = (hddOld > 0d) ? hddNew / hddOld : 1d;
        this.annualSpaceHeat = spaceHeat * setpointScale;

        // (3) redistribution weights, renormalised so Σ thermal == annualSpaceHeat
        double[] weight = new double[n];
        double sumWeight = 0d;
        for (int i = 0; i < n; i++) {
            WeatherSample w = series.get(i);
            if (!inSeason(cfg, w.dayOfYear)) continue;
            double hdd = Math.max(0d, cfg.balancePoint - w.tempC);
            if (hdd <= 0d) continue;
            double windFactor = 1d + cfg.alphaWind * w.windSpeed;
            double hourly = cfg.hourlyProfile[w.hourOfDay];
            double dow = cfg.dowProfile[w.dowIndex];
            weight[i] = hdd * windFactor * hourly * dow;
            sumWeight += weight[i];
        }
        for (int i = 0; i < n; i++) {
            thermalKWh[i] = (sumWeight > 0d) ? annualSpaceHeat * weight[i] / sumWeight : 0d;
        }

        // (4) COP: shape from datasheet curve, level calibrated so realised SCOP == cfg.scop
        double[] copShape = new double[n];
        double sumShapeElec = 0d;
        for (int i = 0; i < n; i++) {
            copShape[i] = Math.max(COP_FLOOR, cfg.copRated + cfg.copSlope * (series.get(i).tempC - cfg.copRefTemp));
            if (thermalKWh[i] > 0d) sumShapeElec += thermalKWh[i] / copShape[i];
        }
        // The realised SCOP under COP = shape·corr is corr·impliedScop, so to hit cfg.scop we need
        // corr = scop / impliedScop. (Scales COP down when the raw shape is over-optimistic, i.e. burns
        // more electricity to match the user's worse seasonal figure — and up in the opposite case.)
        double impliedScop = (sumShapeElec > 0d) ? annualSpaceHeat / sumShapeElec : cfg.scop;
        double corr = (impliedScop > 0d) ? cfg.scop / impliedScop : 1d;

        double calibElec = 0d;
        for (int i = 0; i < n; i++) {
            cop[i] = Math.max(COP_FLOOR, copShape[i] * corr);
            if (thermalKWh[i] > 0d) calibElec += thermalKWh[i] / cop[i];
        }
        this.calibratedElectricity = calibElec;

        // (5) capacity clamp + resistive backup
        double qMax = cfg.capacityKw * cfg.intervalHours;
        double totalElec = 0d;
        for (int i = 0; i < n; i++) {
            double qth = thermalKWh[i];
            if (qth <= 0d) {
                loadKWh[i] = 0d;
                backupKWh[i] = 0d;
                heatDeliveredKWh[i] = 0d;
            } else if (qth <= qMax) {
                loadKWh[i] = qth / cop[i];
                backupKWh[i] = 0d;
                heatDeliveredKWh[i] = qth;
            } else {
                // Heat pump caps out at qMax; the rest is either resistive backup (COP 1, heat conserved)
                // or, with backup disabled, simply not delivered (comfort droop).
                double qBackup = qth - qMax;
                backupKWh[i] = cfg.backupHeater ? qBackup : 0d;
                heatDeliveredKWh[i] = cfg.backupHeater ? qth : qMax;
                loadKWh[i] = qMax / cop[i] + backupKWh[i];
            }
            totalElec += loadKWh[i];
        }
        this.annualElectricity = totalElec;
    }

    /** Electrical load (kWh, {@code heatPumpLoad}) for interval {@code i}. */
    public double loadForIndex(int i) {
        return loadKWh[i];
    }

    /** Thermal demand (kWh) for interval {@code i} — what the home calls for (before any clamp). */
    public double thermalForIndex(int i) {
        return thermalKWh[i];
    }

    /**
     * Heat actually delivered (kWh) for interval {@code i}. Equals the demand except when the capacity is
     * exceeded with the backup heater disabled (comfort droop), where it is capped at {@code capacity·Δt}.
     */
    public double heatDeliveredForIndex(int i) {
        return heatDeliveredKWh[i];
    }

    /** Resistive backup electrical (kWh) for interval {@code i} — the COP-1 top-up, a subset of the load. */
    public double backupForIndex(int i) {
        return backupKWh[i];
    }

    /** Calibrated heat-pump COP for interval {@code i} (temperature-driven; defined even when idle). */
    public double copForIndex(int i) {
        return cop[i];
    }

    /** Outdoor air temperature (°C) for interval {@code i} — the COP driver, passed through for graphs. */
    public double outdoorTempForIndex(int i) {
        return series.get(i).tempC;
    }

    /** Wind speed (m/s) for interval {@code i} — the infiltration-demand driver, passed through for graphs. */
    public double windSpeedForIndex(int i) {
        return series.get(i).windSpeed;
    }

    /** The trusted annual space-heat anchor (kWh) — Σ thermal demand equals this. */
    public double getAnnualSpaceHeat() {
        return annualSpaceHeat;
    }

    /** Annual electricity after capacity clamp + backup (kWh). */
    public double getAnnualElectricity() {
        return annualElectricity;
    }

    /** Annual electricity from the SCOP calibration, before the capacity clamp (kWh) — equals {@code anchor/SCOP}. */
    public double getCalibratedElectricity() {
        return calibratedElectricity;
    }

    // Σ max(0, base − T_out) over the series (degree-hours; the Δt factor cancels in the setpoint ratio).
    private static double hddSum(List<WeatherSample> series, double base) {
        double s = 0d;
        for (WeatherSample w : series) s += Math.max(0d, base - w.tempC);
        return s;
    }

    // Heating-season gate. Unset ⇒ always on. Supports a window that wraps the year end (on > off).
    private static boolean inSeason(Config cfg, int dayOfYear) {
        if (cfg.heatOnDayOfYear == null || cfg.heatOffDayOfYear == null) return true;
        int on = cfg.heatOnDayOfYear, off = cfg.heatOffDayOfYear;
        if (on <= off) return dayOfYear >= on && dayOfYear <= off;
        return dayOfYear >= on || dayOfYear <= off; // wraps (e.g. 1 Oct .. 30 Apr)
    }
}
