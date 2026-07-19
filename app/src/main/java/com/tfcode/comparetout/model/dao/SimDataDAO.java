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

package com.tfcode.comparetout.model.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.tfcode.comparetout.model.ComparisonSenarioRow;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.scenario.MICBreachRow;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulation-output queries — sim CRUD, KPIs, MIC breaches, per-day/line/month/
 * year bar graphs, sum/avg interval aggregations, and sim date ranges — moved
 * verbatim (SQL byte-identical) from ScenarioDAO (mega-refactor C8). Pure
 * abstract queries only. deleteSimulationDataForScenarioID /
 * deleteCostingDataForScenarioID deliberately stay on ScenarioDAO until C9
 * (called by the deleteScenario lifecycle transaction).
 */
@Dao
public abstract class SimDataDAO {
    @Query("DELETE FROM scenariosimulationdata WHERE scenarioID IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID) ")
    public abstract void deleteSimulationDataForProfileID(long loadProfileID);

    @Query("DELETE FROM costings WHERE scenarioID IN (" +
            "SELECT scenarioID FROM scenario2loadprofile WHERE loadProfileID = :loadProfileID) ")
    public abstract void deleteCostingDataForProfileID(long loadProfileID);

    @Query("DELETE FROM scenariosimulationdata WHERE scenarioID = (" +
            "SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID) ")
    public abstract void deleteSimulationDataForPanelID(long panelID);

    @Query("DELETE FROM costings WHERE scenarioID = (" +
            "SELECT scenarioID FROM scenario2panel WHERE panelID = :panelID) ")
    public abstract void deleteCostingDataForPanelID(long panelID);

    @Query("SELECT scenarioIndex FROM scenarios " +
            "WHERE scenarioIndex NOT IN (SELECT DISTINCT scenarioID FROM scenariosimulationdata) " +
            "AND scenarioIndex IN (SELECT DISTINCT scenarioID FROM scenario2loadprofile) " +
            "AND (SELECT DISTINCT loadProfileID FROM scenario2loadprofile WHERE scenarioID = scenarioIndex ) IN (SELECT DISTINCT loadProfileID FROM loadprofiledata)")
    public abstract List<Long> getAllScenariosThatNeedSimulation();

    @Query("SELECT A.date, A.minute, A.load, A.mod, A.dow, A.do2001, 0 AS TPV, A.millisSinceEpoch FROM " +
            "(SELECT * FROM loadprofiledata WHERE loadProfileID = " +
            "(SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID = :scenarioID)) AS A ORDER BY A.date, A.mod")
    public abstract List<SimulationInputData> getSimulationInputNoSolar(long scenarioID);

    @Query("SELECT date, minute, mod, dow, do2001, 0 AS load, pv AS TPV, millisSinceEpoch " +
            "FROM paneldata WHERE panelID = :panelID ORDER BY date, mod")
    public abstract List<SimulationInputData> getPVRowsForPanel(long panelID);

    @Insert(entity = ScenarioSimulationData.class)
    public abstract void saveSimulationDataForScenario(ArrayList<ScenarioSimulationData> simulationData);

    @Query("SELECT * FROM scenariosimulationdata WHERE scenarioID = :scenarioID " +
            "ORDER BY date, minuteOfDay")
    public abstract List<ScenarioSimulationData> getSimulationDataForScenario(long scenarioID);

    /** Count of intervals whose grid import exceeded the MIC (item 4c). capPerInterval = gridImportMax/12 (kWh). */
    @Query("SELECT COUNT(*) FROM scenariosimulationdata WHERE scenarioID = :scenarioID AND buy > :capPerInterval")
    public abstract int countGridImportBreaches(long scenarioID, double capPerInterval);

    /** The worst (highest-import) MIC-breach intervals for a scenario, capped at :limit (item 4c). */
    @Query("SELECT date, minuteOfDay, buy AS buy FROM scenariosimulationdata " +
            "WHERE scenarioID = :scenarioID AND buy > :capPerInterval ORDER BY buy DESC LIMIT :limit")
    public abstract List<MICBreachRow> getTopGridImportBreaches(long scenarioID, double capPerInterval, int limit);

    @Query("SELECT sum(pv) AS gen, SUM(Feed) AS sold, SUM(load) + SUM(immersionLoad) + SUM(directEVcharge) + SUM(heatPumpLoad) AS load, SUM(Buy) AS bought, " +
            "sum(kWHDivToEV) AS evDiv, sum(kWHDivToWater) AS h2oDiv, sum(pvToLoad) AS pvToLoad, sum(pvToCharge) AS pvToCharge, " +
            "sum(load) AS house, sum(immersionLoad) AS h20, sum(directEVcharge) AS EV " +
            "FROM scenariosimulationdata WHERE scenarioID = :scenarioID")
    public abstract SimKPIs getSimKPIsForScenario(Long scenarioID);

    /** Wipe all simulation output (one-time rollout refresh — scenarios re-simulate). */
    @Query("DELETE FROM scenariosimulationdata")
    public abstract void deleteAllSimulationData();

    @Query("SELECT minuteOfDay / 60 AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert, " +
            "sum(battery2Grid) AS Bat2Grid, sum(heatPumpLoad) AS HeatPump, sum(heatPumpBackupLoad) AS HeatPumpBackup, sum(heatPumpHeat) AS HeatPumpHeat, avg(heatPumpCop) AS HeatPumpCop, avg(heatPumpOutdoorTemp) AS HeatPumpTemp, avg(heatPumpWindSpeed) AS HeatPumpWind FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID " +
            "GROUP BY Hour ORDER BY Hour")
    public abstract List<ScenarioBarChartData> getBarData(Long scenarioID, int dayOfYear);

    @Query("SELECT minuteOfDay, SOC, waterTemp, heatPumpCop, heatPumpOutdoorTemp, heatPumpWindSpeed FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear AND scenarioID = :scenarioID  ORDER BY minuteOfDay")
    public abstract List<ScenarioLineGraphData> getLineData(Long scenarioID, int dayOfYear);

    @Query("SELECT substr(Date, 9) AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert, " +
            "sum(battery2Grid) AS Bat2Grid, sum(heatPumpLoad) AS HeatPump, sum(heatPumpBackupLoad) AS HeatPumpBackup, sum(heatPumpHeat) AS HeatPumpHeat, avg(heatPumpCop) AS HeatPumpCop, avg(heatPumpOutdoorTemp) AS HeatPumpTemp, avg(heatPumpWindSpeed) AS HeatPumpWind FROM scenariosimulationdata WHERE substr(Date, 6,2) IN (" +
            "SELECT DISTINCT substr(Date, 6,2) AS Month FROM scenariosimulationdata WHERE dayOf2001 = :dayOfYear) " +
            "AND scenarioID = :scenarioID GROUP BY dayOf2001 ORDER BY dayOf2001")
    public abstract List<ScenarioBarChartData> getMonthlyBarData(Long scenarioID, int dayOfYear);

    @Query("SELECT substr(Date, 6,2) AS Hour, sum(load) AS Load, sum(feed) AS Feed, sum(Buy) AS Buy, " +
            "sum(pv) AS PV, sum(pvToCharge) AS PV2Battery, sum(pvToLoad) AS PV2Load, sum(batToLoad) AS Battery2Load, sum(gridToBattery) AS Grid2Battery, " +
            "sum(directEVcharge) AS EVSchedule, sum(immersionLoad) AS HWSchedule, sum(kWHDivToEV) AS EVDivert, sum(kWHDivToWater) AS HWDivert, " +
            "sum(battery2Grid) AS Bat2Grid, sum(heatPumpLoad) AS HeatPump, sum(heatPumpBackupLoad) AS HeatPumpBackup, sum(heatPumpHeat) AS HeatPumpHeat, avg(heatPumpCop) AS HeatPumpCop, avg(heatPumpOutdoorTemp) AS HeatPumpTemp, avg(heatPumpWindSpeed) AS HeatPumpWind FROM scenariosimulationdata WHERE scenarioID = :scenarioID " +
            "GROUP BY substr(Date, 6,2) ORDER BY substr(Date, 6,2)")
    public abstract List<ScenarioBarChartData> getYearBarData(Long scenarioID);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast ((minuteOfDay / 60) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY  INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumHour(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOY(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOW(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "strftime('%Y', date) || strftime('%m', date) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumMonth(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL " +
            "FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumYear(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast ((minuteOfDay / 60) as INTEGER) AS INTERVAL " +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%j', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgHour(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM ( " +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOY(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%W', date) as integer), INTERVAL ORDER BY INTERVAL" +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOW(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "strftime('%m', date) as INTERVAL" +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN " +
            " GROUP BY INTERVAL ORDER BY INTERVAL, date) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgMonth(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "avg(PV2BAT) AS PV2BAT, avg(PV2LOAD) AS PV2LOAD, avg(BAT2LOAD) AS BAT2LOAD, avg(GRID2BAT) AS GRID2BAT, " +
            "avg(EVSCHEDULE) AS EVSCHEDULE, avg(EVDIVERT) AS EVDIVERT, avg(HWSCHEDULE) AS HWSCHEDULE, avg(HWDIVERT) AS HWDIVERT," +
            "avg(BAT2GRID) AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, avg(PV2GRID) AS PV2GRID, avg(GRID2LOAD) AS GRID2LOAD, avg(EV_ACTUAL) AS EV_ACTUAL, avg(BAT_CHARGE_IN) AS BAT_CHARGE_IN, avg(BAT_DISCHARGE_OUT) AS BAT_DISCHARGE_OUT, avg(HW_ACTUAL) AS HW_ACTUAL, avg(HP_ACTUAL) AS HP_ACTUAL, avg(HEATPUMP) AS HEATPUMP, avg(HEATPUMPBACKUP) AS HEATPUMPBACKUP, avg(HEATPUMPHEAT) AS HEATPUMPHEAT, avg(HEATPUMPCOP) AS HEATPUMPCOP, avg(HEATPUMPTEMP) AS HEATPUMPTEMP, avg(HEATPUMPWIND) AS HEATPUMPWIND, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "sum(pvToCharge) AS PV2BAT, sum(pvToLoad) AS PV2LOAD, sum(batToLoad) AS BAT2LOAD, " +
            "sum(gridToBattery) AS GRID2BAT, sum(directEVcharge) AS EVSCHEDULE, sum(kWHDivToEV) AS EVDIVERT, " +
            "sum(immersionLoad) AS HWSCHEDULE, sum(kWHDivToWater) AS HWDIVERT, sum(battery2Grid) AS BAT2GRID, " +
            "sum(pvToCharge + gridToBattery) AS BAT_CHARGE, sum(batToLoad + battery2Grid) AS BAT_DISCHARGE, " +
            "0 AS PV2GRID, 0 AS GRID2LOAD, 0 AS EV_ACTUAL, 0 AS BAT_CHARGE_IN, 0 AS BAT_DISCHARGE_OUT, 0 AS HW_ACTUAL, 0 AS HP_ACTUAL, sum(heatPumpLoad) AS HEATPUMP, sum(heatPumpBackupLoad) AS HEATPUMPBACKUP, sum(heatPumpHeat) AS HEATPUMPHEAT, avg(heatPumpCop) AS HEATPUMPCOP, avg(heatPumpOutdoorTemp) AS HEATPUMPTEMP, avg(heatPumpWindSpeed) AS HEATPUMPWIND, " +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL" +
            " FROM scenariosimulationdata WHERE date >= :from AND date <= :to AND scenarioID = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL )")
    public abstract List<IntervalRow> avgYear(String sysSN, String from, String to);

    @Query("SELECT cast(scenarioID AS TEXT) AS sysSn, MIN(date) AS start, MAX(date) AS finish FROM scenariosimulationdata GROUP by scenarioID")
    public abstract LiveData<List<InverterDateRange>> loadDateRanges();

    @Query("SELECT DISTINCT 'SIMULATION' AS CATEGORY, scenarioName AS sysSN, scenarioID FROM scenarios, scenariosimulationdata where scenarioID = scenarioIndex " +
            "UNION " +
            "SELECT DISTINCT 'ESBNHDF' AS CATEGORY, sysSn, '0' FROM alphaESSTransformedData")
    public abstract List<ComparisonSenarioRow> getCompareScenarios();

    @Query("SELECT cast(scenarioID AS TEXT) AS sysSn, MIN(date) AS start, MAX(date) AS finish FROM scenariosimulationdata WHERE scenarioID = :sysSN")
    public abstract InverterDateRange getSimDateRanges(String sysSN);

}
