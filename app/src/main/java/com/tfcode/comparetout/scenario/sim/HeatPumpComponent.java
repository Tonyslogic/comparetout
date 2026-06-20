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

import com.tfcode.comparetout.scenario.sim.HeatPumpDemandModel.Config;
import com.tfcode.comparetout.scenario.sim.HeatPumpDemandModel.WeatherSample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The heat pump as an engine component (Phase 2 of {@code plans/hp/plan.md}). A {@link DemandContributor}:
 * each interval it returns the precomputed electrical load for that instant and routes it to
 * {@link OutputChannel#HEAT_PUMP_LOAD}.
 *
 * <p>All the physics — fuel-anchored annual heat, HDD + wind redistribution, SCOP-calibrated COP, capacity
 * clamp + backup — lives in {@link HeatPumpDemandModel}, computed <b>once</b> over the whole weather series
 * (the renormalisation and SCOP calibration are whole-year operations). This component is a thin adapter:
 * it maps an interval's canonical millis to the model's series index and hands back the load.</p>
 *
 * <p><b>v1 is demand-only and stateless across intervals</b> — the load is a pure function of the interval
 * (no thermal store), so there is no reset hook to implement. An interval with no weather sample (a millis
 * the series does not cover) contributes zero, mirroring how the other demand components report zero when
 * nothing applies.</p>
 *
 * <p>The series and its millis come from the weather provider (Phase 3) and, in production, are aligned to
 * the sim grid before the model is built. Here the component receives the already-built model plus the
 * parallel millis array, so it stays independent of how the series was sourced or aligned.</p>
 */
public final class HeatPumpComponent implements DemandContributor {

    private final HeatPumpDemandModel model;
    private final Map<Long, Integer> millisToIndex;

    /**
     * Builds a heat-pump component for a sim grid: aligns the weather onto each grid instant, derives the
     * calendar fields the model needs the <b>same way the engine derives {@link IntervalContext}</b>
     * ({@code SimTime.toLocalDateTime(millis, UTC)} → hour / day-of-week / day-of-year), builds the calibrated
     * {@link HeatPumpDemandModel} over that aligned series, and wraps it. This is the factory the
     * {@code ComponentRegistry} will call in Phase 4.
     *
     * @param cfg        the heat-pump configuration
     * @param weather    the aligned weather source (interpolates onto the grid)
     * @param gridMillis the sim grid instants, in chronological order
     */
    public static HeatPumpComponent build(Config cfg, WeatherProvider weather, long[] gridMillis) {
        List<WeatherSample> aligned = new ArrayList<>(gridMillis.length);
        for (long m : gridMillis) {
            LocalDateTime t = SimTime.toLocalDateTime(m, ZoneOffset.UTC);
            int dowIndex = t.getDayOfWeek().getValue() - 1; // Mon=0..Sun=6
            aligned.add(new WeatherSample(weather.temperatureAt(m), weather.windSpeedAt(m),
                    t.getHour(), dowIndex, t.getDayOfYear()));
        }
        return new HeatPumpComponent(new HeatPumpDemandModel(cfg, aligned), gridMillis);
    }

    /**
     * @param model       the per-interval load model, already built (and calibrated) over the series
     * @param seriesMillis the canonical UTC millis for each model index, in the same order as the series
     */
    public HeatPumpComponent(HeatPumpDemandModel model, long[] seriesMillis) {
        this.model = model;
        this.millisToIndex = new HashMap<>(Math.max(16, seriesMillis.length * 2));
        for (int i = 0; i < seriesMillis.length; i++) {
            millisToIndex.put(seriesMillis[i], i);
        }
    }

    @Override
    public DemandResult demand(IntervalContext ctx) {
        Integer idx = millisToIndex.get(ctx.millis);
        Map<OutputChannel, Double> outputs = new EnumMap<>(OutputChannel.class);
        if (idx == null) {
            // No weather for this instant ⇒ no contribution (every aligned sim interval will have one).
            outputs.put(OutputChannel.HEAT_PUMP_LOAD, 0d);
            outputs.put(OutputChannel.HEAT_PUMP_BACKUP_LOAD, 0d);
            outputs.put(OutputChannel.HEAT_PUMP_HEAT, 0d);
            outputs.put(OutputChannel.HEAT_PUMP_COP, 0d);
            outputs.put(OutputChannel.HEAT_PUMP_OUTDOOR_TEMP, 0d);
            outputs.put(OutputChannel.HEAT_PUMP_WIND_SPEED, 0d);
            return new DemandResult(0d, outputs);
        }
        double load = model.loadForIndex(idx);
        outputs.put(OutputChannel.HEAT_PUMP_LOAD, load);
        outputs.put(OutputChannel.HEAT_PUMP_BACKUP_LOAD, model.backupForIndex(idx));
        outputs.put(OutputChannel.HEAT_PUMP_HEAT, model.heatDeliveredForIndex(idx));
        outputs.put(OutputChannel.HEAT_PUMP_COP, model.copForIndex(idx));
        outputs.put(OutputChannel.HEAT_PUMP_OUTDOOR_TEMP, model.outdoorTempForIndex(idx));
        outputs.put(OutputChannel.HEAT_PUMP_WIND_SPEED, model.windSpeedForIndex(idx));
        return new DemandResult(load, outputs);
    }
}
