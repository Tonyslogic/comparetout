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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The wind→price model behind Layer-B foresight, calibrated from one year of
 * paired series: the plan's half-hourly prices × the CDS/ERA5 wind for the
 * same dates. Wind drives the I-SEM wholesale level (high wind → cheap), so
 * a wind outlook is a usable price outlook.
 *
 * <p>Quantile mapping, per plan §2f: days are grouped by month and ranked by
 * mean wind; the expected prices at wind-percentile p are the average of the
 * {@code K_NEAREST} days nearest that rank, per 6-hour band (morning /
 * daytime / evening / night structure survives, day-specific noise averages
 * out). Nonparametric — it reproduces whatever monotone relation the
 * calibration year actually shows, without assuming a direction or a curve.
 */
public final class WindPriceCalibration {

    /** One calibration day: its mean wind and its 48 half-hourly prices. */
    public static final class DaySample {
        public final LocalDate date;
        public final double meanWind;
        public final double[] buy;

        public DaySample(LocalDate date, double meanWind, double[] buy) {
            this.date = date;
            this.meanWind = meanWind;
            this.buy = buy;
        }
    }

    static final int BANDS = 4;               // 6-hour bands
    private static final int SLOTS_PER_BAND = 48 / BANDS;
    private static final int K_NEAREST = 5;

    /** Per month (1-12): samples sorted by mean wind ascending. */
    private final Map<Integer, List<DaySample>> byMonth = new HashMap<>();
    /** Per month: band-mean prices parallel to the sorted samples. */
    private final Map<Integer, double[][]> bandPricesByMonth = new HashMap<>();

    private WindPriceCalibration() {
    }

    public static WindPriceCalibration calibrate(List<DaySample> days) {
        WindPriceCalibration cal = new WindPriceCalibration();
        for (DaySample day : days) {
            cal.byMonth.computeIfAbsent(day.date.getMonthValue(), m -> new ArrayList<>()).add(day);
        }
        for (Map.Entry<Integer, List<DaySample>> month : cal.byMonth.entrySet()) {
            List<DaySample> samples = month.getValue();
            samples.sort(Comparator.comparingDouble(s -> s.meanWind));
            double[][] bands = new double[samples.size()][BANDS];
            for (int i = 0; i < samples.size(); i++) {
                for (int b = 0; b < BANDS; b++) {
                    double sum = 0;
                    for (int s = b * SLOTS_PER_BAND; s < (b + 1) * SLOTS_PER_BAND; s++) {
                        sum += samples.get(i).buy[s];
                    }
                    bands[i][b] = sum / SLOTS_PER_BAND;
                }
            }
            cal.bandPricesByMonth.put(month.getKey(), bands);
        }
        return cal;
    }

    /** True when the month has enough samples to predict from. */
    public boolean covers(int month) {
        List<DaySample> samples = byMonth.get(month);
        return !(null == samples) && samples.size() >= K_NEAREST;
    }

    /**
     * Where a wind speed sits within the month's calibration days, 0..1
     * (0 = calmest day seen, 1 = windiest).
     */
    public double windPercentile(int month, double meanWind) {
        List<DaySample> samples = byMonth.get(month);
        if (null == samples || samples.isEmpty()) return 0.5;
        int below = 0;
        for (DaySample s : samples) {
            if (s.meanWind < meanWind) below++;
        }
        if (samples.size() == 1) return 0.5;
        // A wind above every calibration day ranks 1.0, not size/(size-1).
        return Math.min(1d, (double) below / (samples.size() - 1));
    }

    /**
     * Expected half-hourly prices for a day of this month at the given wind
     * percentile: the mean band prices of the {@code K_NEAREST} calibration
     * days nearest that wind rank, stepped out to 48 slots.
     */
    public double[] expectedPrices(int month, double windPercentile) {
        double[][] bands = bandPricesByMonth.get(month);
        List<DaySample> samples = byMonth.get(month);
        double[] out = new double[48];
        if (null == bands || null == samples || samples.isEmpty()) return out;
        double clamped = Math.max(0, Math.min(1, windPercentile));
        int centre = (int) Math.round(clamped * (samples.size() - 1));
        int from = Math.max(0, centre - K_NEAREST / 2);
        int to = Math.min(samples.size(), from + K_NEAREST);
        from = Math.max(0, to - K_NEAREST);
        for (int b = 0; b < BANDS; b++) {
            double sum = 0;
            for (int i = from; i < to; i++) sum += bands[i][b];
            double bandPrice = sum / (to - from);
            for (int s = b * SLOTS_PER_BAND; s < (b + 1) * SLOTS_PER_BAND; s++) {
                out[s] = bandPrice;
            }
        }
        return out;
    }
}
