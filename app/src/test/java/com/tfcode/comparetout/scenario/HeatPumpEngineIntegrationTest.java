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

package com.tfcode.comparetout.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;
import com.tfcode.comparetout.scenario.sim.CsvWeatherProvider;
import com.tfcode.comparetout.scenario.sim.HeatPumpComponent;
import com.tfcode.comparetout.scenario.sim.HeatPumpDemandModel;
import com.tfcode.comparetout.scenario.sim.IntervalContext;
import com.tfcode.comparetout.scenario.sim.SimTime;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase-4 end-to-end check that a registered heat pump, run through the real {@link SimulationEngine},
 * populates the {@code heatPump*} output columns and that the populated load matches the model. Uses a
 * direct-assertion engine run (not a golden CSV — the golden serialiser keeps its fixed column set, so
 * existing goldens stay byte-identical; design §8 / plans/hp/plan.md Phase 4 note).
 */
public class HeatPumpEngineIntegrationTest {

    private static final File FIXTURE =
            new File("src/test/resources/hp-weather/era5-timeseries-2001-synthetic.csv");
    private static final int ROWS = 288;             // one winter day at 5-minute resolution
    private static final double EXPORT_MAX = 6.0;

    @Test
    public void heatPumpPopulatesOutputColumnsAndMatchesTheModel() throws Exception {
        // A cold January day so heating demand is clearly non-zero.
        double[] load = new double[ROWS];
        double[] pv = new double[ROWS];
        for (int i = 0; i < ROWS; i++) load[i] = 0.15;
        List<SimulationInputData> series = SimSeries.of(LocalDateTime.of(2001, 1, 15, 0, 0), load, pv);

        // Grid millis, derived exactly as the engine derives ctx.millis (date + mod, UTC; no millis stored).
        long[] gridMillis = new long[series.size()];
        for (int i = 0; i < series.size(); i++) {
            SimulationInputData r = series.get(i);
            gridMillis[i] = SimTime.fromDateAndMinuteOfDay(r.getDate(), r.getMod(), ZoneOffset.UTC);
        }

        CsvWeatherProvider weather = new CsvWeatherProvider(new FileReader(FIXTURE));
        HeatPumpDemandModel.Config cfg = new HeatPumpDemandModel.Config();
        cfg.capacityKw = 20d;            // generous, so no clamp/backup muddies the comparison
        cfg.intervalHours = 1d / 12d;    // 5-minute grid
        HeatPumpComponent heatPump = HeatPumpComponent.build(cfg, weather, gridMillis);

        ScenarioInputs scenario = new ScenarioInputs(null, false, null, null, null, EXPORT_MAX, heatPump);
        Inverter inverter = InverterBuilder.anInverter().index(1).name("INV1").build();
        Map<Inverter, SimulationEngine.InputData> map = new LinkedHashMap<>();
        map.put(inverter, new SimulationEngine.InputData(inverter, series, null, null, null));

        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            SimulationEngine.processOneRow(1L, scenario, out, row, map);
        }

        // Expected per-interval load straight from the component (engine writes exactly this).
        double expectedLoad = 0d;
        for (long m : gridMillis) {
            expectedLoad += heatPump.demand(new IntervalContext(m, 1, 1, 0, 0, 1d / 12d)).kWh;
        }

        double sumLoad = 0d, sumHeat = 0d;
        for (ScenarioSimulationData r : out) {
            sumLoad += r.getHeatPumpLoad();
            sumHeat += r.getHeatPumpHeat();
            assertTrue("COP populated", r.getHeatPumpCop() > 0d);
        }
        assertTrue("a winter day must draw heat-pump electricity", sumLoad > 0d);
        assertTrue("heat delivered must be positive", sumHeat > 0d);
        assertEquals("engine heatPumpLoad must equal the model", expectedLoad, sumLoad, 1e-9);

        // Routing spot-check: outdoor temp on a sampled row equals the provider's interpolated value.
        ScenarioSimulationData mid = out.get(120);
        assertEquals(weather.temperatureAt(gridMillis[120]), mid.getHeatPumpOutdoorTemp(), 1e-9);
    }
}
