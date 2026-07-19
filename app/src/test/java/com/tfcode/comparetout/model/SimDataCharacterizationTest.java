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

package com.tfcode.comparetout.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.scenario.MICBreachRow;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.testdata.FullScenarioFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Characterization of the simulation-output read surface through the public
 * {@link ToutcRepository} API. Rows are hand-built constants so every
 * aggregate has a closed form: 2 days (2001-01-01 do2001=1 and 2001-06-01
 * do2001=152) × 24 hourly rows.
 */
public class SimDataCharacterizationTest extends CharacterizationTestBase {

    private static final double D = 1e-9;
    private static final int ROWS = 48;
    private static final double LOAD = 1.0, PV = 0.5, BUY = 0.6, FEED = 0.2;
    private static final double PV2LOAD = 0.3, PV2CHG = 0.1, BAT2LOAD = 0.2;
    private static final double GRID2BAT = 0.15, BAT2GRID = 0.02;
    private static final double EV = 0.2, HW = 0.1, EVDIV = 0.05, HWDIV = 0.04;
    private static final double HP = 0.3, HPBACKUP = 0.01, HPHEAT = 0.9;

    private long id;

    @Before
    public void seed() {
        id = repo.insertScenarioAndReturnID(FullScenarioFixture.components(), false);
        assertTrue(id > 0);
        ArrayList<ScenarioSimulationData> rows = new ArrayList<>();
        addDay(rows, "2001-01-01", 1, 1, 978307200000L);
        addDay(rows, "2001-06-01", 152, 5, 991353600000L);
        repo.saveSimulationDataForScenario(rows);
    }

    private void addDay(ArrayList<ScenarioSimulationData> rows,
                        String date, int do2001, int dow, long dayMillis) {
        for (int h = 0; h < 24; h++) {
            ScenarioSimulationData r = new ScenarioSimulationData();
            r.setScenarioID(id);
            r.setDate(date);
            r.setMinuteOfDay(h * 60);
            r.setDayOfWeek(dow);
            r.setDayOf2001(do2001);
            r.setMillisSinceEpoch(dayMillis + h * 3600_000L);
            r.setLoad(LOAD);
            r.setPv(PV);
            r.setBuy(BUY);
            r.setFeed(FEED);
            r.setPvToLoad(PV2LOAD);
            r.setPvToCharge(PV2CHG);
            r.setBatToLoad(BAT2LOAD);
            r.setGridToBattery(GRID2BAT);
            r.setBattery2Grid(BAT2GRID);
            r.setDirectEVcharge(EV);
            r.setImmersionLoad(HW);
            r.setKWHDivToEV(EVDIV);
            r.setKWHDivToWater(HWDIV);
            r.setHeatPumpLoad(HP);
            r.setHeatPumpBackupLoad(HPBACKUP);
            r.setHeatPumpHeat(HPHEAT);
            r.setHeatPumpCop(3.0);
            r.setHeatPumpOutdoorTemp(8.0);
            r.setHeatPumpWindSpeed(4.0);
            r.setSOC(h * 100.0 / 24.0);
            r.setWaterTemp(55.0);
            rows.add(r);
        }
    }

    @Test
    public void savedRowsRoundTripOrderedAndDateRanged() {
        List<ScenarioSimulationData> back = repo.getSimulationDataForScenario(id);
        assertEquals(ROWS, back.size());
        assertEquals("2001-01-01", back.get(0).getDate());
        assertEquals(0, back.get(0).getMinuteOfDay());
        assertEquals("2001-06-01", back.get(ROWS - 1).getDate());
        assertEquals(23 * 60, back.get(ROWS - 1).getMinuteOfDay());

        InverterDateRange range = repo.getSimDateRanges(String.valueOf(id));
        assertEquals(String.valueOf(id), range.sysSn);
        assertEquals("2001-01-01", range.startDate);
        assertEquals("2001-06-01", range.finishDate);
    }

    @Test
    public void kpisMatchClosedForm() {
        SimKPIs k = repo.getSimKPIsForScenario(id);
        assertEquals(ROWS * PV, k.generated, D);
        assertEquals(ROWS * FEED, k.sold, D);
        assertEquals(ROWS * (LOAD + HW + EV + HP), k.totalLoad, D);
        assertEquals(ROWS * BUY, k.bought, D);
        assertEquals(ROWS * EVDIV, k.evDiv, D);
        assertEquals(ROWS * HWDIV, k.h2oDiv, D);
        assertEquals(ROWS * PV2LOAD, k.pvToLoad, D);
        assertEquals(ROWS * PV2CHG, k.pvToCharge, D);
        assertEquals(ROWS * LOAD, k.house, D);
        assertEquals(ROWS * HW, k.h20, D);
        assertEquals(ROWS * EV, k.ev, D);
    }

    @Test
    public void dayGraphsBucketHourlyAndTraceMinutes() {
        List<ScenarioBarChartData> bars = repo.getBarData(id, 1);
        assertEquals(24, bars.size());
        for (int h = 0; h < 24; h++) {
            assertEquals(h, bars.get(h).hour);
            assertEquals(LOAD, bars.get(h).load, D);
            assertEquals(PV, bars.get(h).pv, D);
            assertEquals(HP, bars.get(h).heatPump, D);
        }

        List<ScenarioLineGraphData> line = repo.getLineData(id, 1);
        assertEquals(24, line.size());
        assertEquals(0, line.get(0).mod);
        assertEquals(0.0, line.get(0).soc, D);
        assertEquals(55.0, line.get(0).waterTemperature, D);
        assertEquals(23 * 100.0 / 24.0, line.get(23).soc, D);
    }

    @Test
    public void monthAndYearGraphsGroupByDayAndMonth() {
        // Month view for day 1: every day in January — the fixture has one.
        List<ScenarioBarChartData> monthly = repo.getMonthlyBarData(id, 1);
        assertEquals(1, monthly.size());
        assertEquals(24 * LOAD, monthly.get(0).load, D);

        // Year view: one bucket per month with data (Jan + Jun).
        List<ScenarioBarChartData> year = repo.getYearBarData(id);
        assertEquals(2, year.size());
        assertEquals(24 * LOAD, year.get(0).load, D);
        assertEquals(24 * LOAD, year.get(1).load, D);
    }

    @Test
    public void simSeriesSumAndAvgAggregations() {
        String sid = String.valueOf(id);
        String from = "2001-01-01", to = "2001-06-01";

        List<IntervalRow> sumHour = repo.getSimSumHour(sid, from, to);
        assertEquals(24, sumHour.size());
        assertEquals(2 * LOAD, sumHour.get(0).load, D);
        assertEquals(2 * BUY, sumHour.get(0).buy, D);
        assertEquals(2 * (PV2CHG + GRID2BAT), sumHour.get(0).batCharge, D);

        List<IntervalRow> sumDoy = repo.getSimSumDOY(sid, from, to);
        assertEquals(2, sumDoy.size());
        assertEquals(24 * LOAD, sumDoy.get(0).load, D);

        List<IntervalRow> sumMonth = repo.getSimSumMonth(sid, from, to);
        assertEquals(2, sumMonth.size());
        assertEquals(24 * PV, sumMonth.get(0).pv, D);

        // Hourly average across the two days is the per-day value.
        List<IntervalRow> avgHour = repo.getSimAvgHour(sid, from, to);
        assertEquals(24, avgHour.size());
        assertEquals(LOAD, avgHour.get(0).load, D);

        List<IntervalRow> avgYear = repo.getSimAvgYear(sid, from, to);
        assertEquals(1, avgYear.size());
        assertEquals(ROWS * LOAD, avgYear.get(0).load, D);
    }

    @Test
    public void micBreachCountingAndWorstList() {
        assertEquals(ROWS, repo.countGridImportBreaches(id, BUY - 0.1));
        assertEquals(0, repo.countGridImportBreaches(id, BUY + 0.1));

        List<MICBreachRow> top = repo.getTopGridImportBreaches(id, BUY - 0.1, 5);
        assertEquals(5, top.size());
        assertEquals(BUY, top.get(0).buy, D);
    }

    @Test
    public void ancillaryScenarioQueries() {
        // Characterized contract: "may need costing" is simply every scenario.
        assertTrue(repo.getAllScenariosThatMayNeedCosting().contains(id));
        assertFalse(repo.getCompareScenarios().isEmpty());
        assertEquals(6.0, repo.getGridExportMaxForScenario(id), D);
    }

    @Test
    public void deleteScenarioSimDataClearsTheSurface() {
        repo.deleteSimulationDataForScenarioID(id);
        awaitDbWrites();
        assertTrue(repo.getSimulationDataForScenario(id).isEmpty());
        assertEquals(0.0, repo.getSimKPIsForScenario(id).generated, D);
        assertEquals(0, repo.getBarData(id, 1).size());
    }
}
