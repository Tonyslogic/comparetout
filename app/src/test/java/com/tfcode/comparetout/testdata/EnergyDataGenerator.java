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

package com.tfcode.comparetout.testdata;

import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic synthetic energy-data generator for tests — the code-first
 * alternative to committing captured data blobs (mirrors the approach of
 * {@code app/src/test/resources/hp-weather/generate_synthetic_2001.py}).
 *
 * <p>Nothing here ships in any APK: this lives in the unit-test source set. It
 * produces {@link AlphaESSTransformedData} rows on the 5-minute grid with
 * realistic shapes (a seasonal PV bell, a base load with morning/evening
 * humps, a simple self-consumption battery, and grid buy/feed as the residual
 * balance), so DB/Compare/Costing tests exercise plausible data without a
 * fixture file.
 *
 * <p>The per-interval PV and load <b>shapes</b> are exposed as pure,
 * RNG-free static functions ({@link #pvKwh} / {@link #loadKwh}) so the
 * simulation golden-master tests can drive the engine from the same profiles
 * and keep stable approved snapshots. The stateful {@link #generate} path adds
 * optional seeded jitter (safe there, because those tests treat the returned
 * rows themselves as the oracle).
 *
 * <p>Units are kWh accumulated within one 5-minute interval (so 0.25 kWh in a
 * slot is an average of 3 kW), matching {@code SimulationInputData} and the
 * existing golden-master {@code bellPV} helper.
 */
public final class EnergyDataGenerator {

    public static final int INTERVALS_PER_DAY = 288;
    private static final double SLOT_HOURS = 5.0 / 60.0;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN = DateTimeFormatter.ofPattern("HH:mm");

    // Battery model used only by generate() (the engine models its own battery
    // in the golden-master path).
    private static final double BATTERY_KWH = 5.0;
    private static final double BATTERY_SLOT_RATE = 0.25;   // kWh per 5-minute slot (~3 kW)
    private static final double BATTERY_FLOOR = 0.10;       // keep 10% in reserve

    private EnergyDataGenerator() {
    }

    // ── pure per-interval shapes (no RNG — safe for golden snapshots) ─────────

    /**
     * PV output for one 5-minute interval, kWh. Seasonal amplitude and a
     * daylight window that widens in summer; zero overnight.
     *
     * @param intervalOfDay 0..287 (interval * 5 minutes past local midnight)
     * @param dayOfYear     1..366
     */
    public static double pvKwh(int intervalOfDay, int dayOfYear) {
        double seasonal = seasonalFactor(dayOfYear);          // 0 (winter) .. 1 (summer)
        double peak = 0.06 + (0.28 - 0.06) * seasonal;        // kWh/slot: ~0.7 kW winter, ~3.4 kW summer
        double sunrise = 8.0 - 3.0 * seasonal;                // 08:00 winter .. 05:00 summer
        double sunset = 16.0 + 5.0 * seasonal;                // 16:00 winter .. 21:00 summer
        double hour = intervalOfDay * 5.0 / 60.0;
        if (hour <= sunrise || hour >= sunset) return 0.0;
        double frac = (hour - sunrise) / (sunset - sunrise);  // 0..1 across daylight
        return peak * Math.sin(Math.PI * frac);
    }

    /**
     * Household load for one 5-minute interval, kWh. A flat base (a little
     * higher in winter) plus a morning and a heavier evening hump.
     */
    public static double loadKwh(int intervalOfDay, int dayOfYear) {
        double seasonal = seasonalFactor(dayOfYear);
        double hour = intervalOfDay * 5.0 / 60.0;
        double base = 0.04 + 0.02 * (1.0 - seasonal);
        double morning = 0.15 * gaussian(hour, 7.5, 1.3);
        double evening = (0.22 + 0.06 * (1.0 - seasonal)) * gaussian(hour, 19.0, 2.4);
        return base + morning + evening;
    }

    /** 1.0 at the June solstice (~doy 172), ~0 at the December solstice. */
    private static double seasonalFactor(int dayOfYear) {
        return 0.5 + 0.5 * Math.cos(2.0 * Math.PI * (dayOfYear - 172) / 365.25);
    }

    private static double gaussian(double x, double mu, double sigma) {
        double z = (x - mu) / sigma;
        return Math.exp(-0.5 * z * z);
    }

    // ── row generation (stateful battery + grid balance) ──────────────────────

    /** Convenience: no jitter (fully deterministic rows). */
    public static List<AlphaESSTransformedData> generate(
            String sysSn, LocalDate from, LocalDate to, ZoneId zone) {
        return generate(sysSn, from, to, zone, 0L, 0.0);
    }

    /**
     * 5-minute rows for {@code [from, to]} inclusive, one source.
     *
     * @param seed      RNG seed (only used when {@code jitter > 0})
     * @param jitter    fractional per-sample noise (e.g. 0.05 = ±5%); 0 = deterministic
     */
    public static List<AlphaESSTransformedData> generate(
            String sysSn, LocalDate from, LocalDate to, ZoneId zone, long seed, double jitter) {
        List<AlphaESSTransformedData> rows = new ArrayList<>();
        Random rng = new Random(seed);
        double soc = 0.5 * BATTERY_KWH;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            int doy = day.getDayOfYear();
            for (int i = 0; i < INTERVALS_PER_DAY; i++) {
                double pv = pvKwh(i, doy);
                double load = loadKwh(i, doy);
                if (jitter > 0) {
                    pv = Math.max(0, pv * (1 + rng.nextGaussian() * jitter));
                    load = Math.max(0, load * (1 + rng.nextGaussian() * jitter));
                }

                // Simple self-consumption battery: soak up surplus, cover deficit.
                double chargeSigned; // + charging, - discharging (HA/Solis convention)
                double surplus = pv - load;
                if (surplus > 0) {
                    double room = BATTERY_KWH - soc;
                    double charge = Math.min(Math.min(surplus, BATTERY_SLOT_RATE), room);
                    soc += charge;
                    chargeSigned = charge;
                } else {
                    double available = soc - BATTERY_FLOOR * BATTERY_KWH;
                    double discharge = Math.min(Math.min(-surplus, BATTERY_SLOT_RATE),
                            Math.max(0, available));
                    soc -= discharge;
                    chargeSigned = -discharge;
                }

                // Grid is the residual of the balance: pv + buy + discharge = load + charge + feed.
                double net = load - pv + chargeSigned;
                double buy = Math.max(0, net);
                double feed = Math.max(0, -net);

                LocalTime time = LocalTime.of(i / 12, (i % 12) * 5);
                ZonedDateTime zdt = ZonedDateTime.of(day, time, zone);
                AlphaESSTransformedData row = new AlphaESSTransformedData();
                row.setSysSn(sysSn);
                row.setDate(day.format(DATE));
                row.setMinute(time.format(MIN));
                row.setMillisSinceEpoch(zdt.toInstant().toEpochMilli());
                row.setPv(round(pv));
                row.setLoad(round(load));
                row.setBuy(round(buy));
                row.setFeed(round(feed));
                row.setCharge(round(chargeSigned));
                rows.add(row);
            }
        }
        return rows;
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
