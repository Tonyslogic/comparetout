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
import java.util.List;

/**
 * Builds the D+2..D+{@value #HORIZON_DAYS} outlook for one day: realised
 * wind stands in for the forecast (no archived forecasts exist — accepted,
 * plan §2f), with the forecast error emulated by a deterministic
 * perturbation of the wind percentile whose amplitude grows with the
 * horizon, and a confidence that decays the further out the day is.
 *
 * <p>Everything is deterministic in (date, horizon) so a regenerated
 * scenario is identical to its previous run — same reproducibility rule as
 * the price materialiser.
 */
public final class LayerBOutlook {

    /** Wind for a 2001-calendar date; null when the weather series lacks it. */
    public interface DailyWind {
        Double meanWind(LocalDate date);
    }

    static final int HORIZON_DAYS = 10;
    /** Confidence multiplier per extra day of horizon. */
    static final double DECAY_PER_DAY = 0.85;
    /** Wind-percentile perturbation amplitude per day of horizon. */
    static final double PERTURB_PER_DAY = 0.04;

    private LayerBOutlook() {
    }

    /**
     * @param baseConfidence 1.0 with matching-year calibration weather; the
     *                       §2f haircut (e.g. 0.5) when falling back to a
     *                       different year's shape.
     */
    public static List<DayOutlook> outlookFor(LocalDate day, WindPriceCalibration calibration,
                                              DailyWind wind, double baseConfidence) {
        List<DayOutlook> outlook = new ArrayList<>();
        for (int k = 2; k <= HORIZON_DAYS; k++) {
            LocalDate target = day.plusDays(k);
            if (target.getYear() != day.getYear()) break;
            if (!calibration.covers(target.getMonthValue())) continue;
            Double meanWind = wind.meanWind(target);
            if (null == meanWind) continue;
            double percentile = calibration.windPercentile(target.getMonthValue(), meanWind);
            double perturbed = clamp01(percentile + noise(target, k) * PERTURB_PER_DAY * k);
            double[] expected = calibration.expectedPrices(target.getMonthValue(), perturbed);
            double confidence = baseConfidence * Math.pow(DECAY_PER_DAY, k - 1);
            outlook.add(new DayOutlook(target, expected, confidence));
        }
        return outlook;
    }

    /** Deterministic pseudo-noise in [-1, 1] from (date, horizon). */
    static double noise(LocalDate date, int horizon) {
        long h = date.toEpochDay() * 31L + horizon;
        h ^= (h << 13);
        h ^= (h >>> 7);
        h ^= (h << 17);
        // Map the mixed bits onto [-1, 1).
        return ((h & 0xFFFFL) / 32768d) - 1d;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
