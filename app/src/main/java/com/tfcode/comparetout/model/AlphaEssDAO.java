/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.tfcode.comparetout.model.importers.CostInputRow;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.importers.alphaess.KPIRow;
import com.tfcode.comparetout.model.importers.alphaess.KeyStatsRow;
import com.tfcode.comparetout.model.importers.alphaess.MaxCalcRow;
import com.tfcode.comparetout.model.importers.alphaess.ScheduleRIInput;

import java.util.List;


@Dao
public abstract class AlphaEssDAO {

    @Insert
    public abstract void addRawEnergy(AlphaESSRawEnergy energy);

    @Insert (onConflict = OnConflictStrategy.IGNORE)
    public abstract void addRawPower(List<AlphaESSRawPower> power);

    @Insert (onConflict = OnConflictStrategy.REPLACE)
    public abstract void addTransformedData(List<AlphaESSTransformedData> data);

    @Transaction
    public void clearAlphaESSDataForSN(String systemSN) {
        deleteAlphaESSPowerForSN(systemSN);
        deleteAlphaESSEnergyForSN(systemSN);
        deleteAlphaESSTransformedForSN(systemSN);
    }

    @Query("DELETE FROM alphaESSTransformedData WHERE sysSn = :systemSN")
    public abstract void deleteAlphaESSTransformedForSN(String systemSN);

    @Query("DELETE FROM alphaESSRawEnergy WHERE sysSn = :systemSN")
    public abstract void deleteAlphaESSEnergyForSN(String systemSN);

    @Query("DELETE FROM alphaESSRawPower WHERE sysSn = :systemSN")
    public abstract void deleteAlphaESSPowerForSN(String systemSN);

    @Query("SELECT sysSn, MIN(theDate) AS start, MAX(theDate) AS finish FROM alphaESSRawEnergy GROUP by sysSn")
    public abstract LiveData<List<InverterDateRange>> loadDateRanges();

    @Query("SELECT sysSn, MIN(date) AS start, MAX(date) AS finish FROM alphaESSTransformedData GROUP by sysSn")
    public abstract LiveData<List<InverterDateRange>> loadESBNHDFDateRanges();

    @Query("SELECT sysSn, MIN(date) AS start, MAX(date) AS finish FROM alphaESSTransformedData GROUP by sysSn")
    public abstract LiveData<List<InverterDateRange>> loadHomeAssistantDateRange();

    @Query("SELECT CASE WHEN EXISTS (SELECT sysSn, theDate FROM alphaESSRawEnergy WHERE sysSn = :sysSn AND theDate = :date) THEN 1 ELSE 0 END AS date_exists")
    public abstract boolean checkSysSnForDataOnDate(String sysSn, String date);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "cast (strftime('%H', minute) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY  INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumHour(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
    "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOY(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOW(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "strftime('%Y', date) || strftime('%m', date) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumMonth(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumYear(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "cast (strftime('%H', minute) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%j', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgHour(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "INTERVAL FROM ( " +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOY(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%W', date) as integer), INTERVAL ORDER BY INTERVAL" +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOW(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, strftime('%m', date) as INTERVAL" +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY INTERVAL ORDER BY INTERVAL, date) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgMonth(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%Y', date) as INTEGER) AS INTERVAL" +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL )")
    public abstract List<IntervalRow> avgYear(String sysSN, String from, String to);

    @Query("SELECT date || ' ' || minute || ':00' AS DATE_TIME, SUM(buy) AS BUY, SUM(feed) as FEED " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            "GROUP BY date, substr(minute, 0, instr(minute,':')) ORDER BY DATE_TIME")
    public abstract List<CostInputRow> getSelectedAlphaESSData(String sysSN, String from, String to);

    @Query("SELECT * FROM alphaESSRawPower WHERE sysSn = :serialNumber AND uploadTime LIKE :from || '%'")
    public abstract List<AlphaESSRawPower> getAlphaESSPowerForSharing(String serialNumber, String from);

    @Query("SELECT * FROM alphaESSRawEnergy WHERE sysSn = :serialNumber ")
    public abstract List<AlphaESSRawEnergy> getAlphaESSEnergyForSharing(String serialNumber);

    @Query("SELECT * FROM alphaESSRawEnergy WHERE sysSn = :serialNumber AND theDate = :date")
    public abstract AlphaESSRawEnergy getAlphaESSEnergyForDate(String serialNumber, String date);

    @Query("SELECT DISTINCT theDate FROM alphaESSRawEnergy WHERE sysSn = :serialNumber ORDER BY theDate ASC")
    public abstract List<String> getExportDatesForSN(String serialNumber);

    @Query("SELECT main.Month, " +
            "tot AS 'PV tot (kWh)', " +
            "best || ' on ' || bestday AS 'Best', " +
            "worst.bad || ' on ' || badday AS 'Worst' " +
            "FROM  " +
            "(SELECT substr(theDate, 3, 5) AS Month, " +
            "SUM(energypv) AS tot, " +
            "MAX(energypv) AS best, " +
            "substr(theDate,9,2) AS bestday " +
            "FROM alphaESSRawEnergy " +
            "WHERE theDate >= :from AND theDate <= :to AND sysSn = :systemSN " +
            "GROUP BY Month " +
            ") AS main, " +
            "( " +
            "SELECT substr(theDate, 3, 5) AS bMonth, " +
            "MIN(energypv) AS bad, " +
            "substr(theDate,9,2) AS badday " +
            "FROM alphaESSRawEnergy " +
            "WHERE theDate >= :from AND theDate <= :to AND sysSn = :systemSN " +
            "GROUP BY bMonth " +
            ") AS worst " +
            "WHERE worst.bMonth = main.Month " +
            "GROUP BY Month ORDER BY Month ASC")
    public abstract List<KeyStatsRow> getKeyStats(String from, String to, String systemSN);

    @Query(" SELECT main.Month, " +
            "ROUND(tot, 2) AS 'PV tot (kWh)', " +
            "ROUND(best, 2) || ' on ' || bestday AS 'Best', " +
            "ROUND(worst.bad, 2) || ' on ' || badday AS 'Worst' " +
            "FROM  " +
            "(" +
            "SELECT substr(cMonth, 1,5) AS Month, SUM(bad) AS tot, MAX(bad) AS best, badday AS bestday FROM ( \n" +
            "SELECT substr(date, 3) AS cMonth, \n" +
            "SUM(pv) AS bad, \n" +
            "substr(date,9,2) AS badday \n" +
            "FROM alphaESSTransformedData \n" +
            "WHERE sysSn = :systemSN AND date >= :from AND date <= :to " +
            "GROUP BY cMonth ORDER BY cMonth ) \n" +
            "GROUP BY Month" +
            ") AS main, " +
            "( " +
            "SELECT substr(cMonth, 1,5) AS bMonth, MIN(bad) AS bad, badday FROM ( " +
            "SELECT substr(date, 3) AS cMonth, " +
            "SUM(pv) AS bad, " +
            "substr(date,9,2) AS badday " +
            "FROM alphaESSTransformedData " +
            "WHERE sysSn = :systemSN AND date >= :from AND date <= :to " +
            "GROUP BY cMonth ORDER BY cMonth ) " +
            "GROUP BY bMonth " +
            ") AS worst " +
            "WHERE worst.bMonth = main.Month " +
            "GROUP BY Month ORDER BY Month ASC")
    public abstract List<KeyStatsRow> getHAKeyStats(String from, String to, String systemSN);

    @Query("SELECT ((sum(pv) - sum(feed)) / sum(pv)) * 100 AS SC, ((sum(pv) - sum(feed)) / sum(load)) * 100 AS SS " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :systemSN")
    public abstract KPIRow getKPIs(String from, String to, String systemSN);

    @Query("SELECT * FROM alphaESSTransformedData WHERE sysSn = :systemSN AND date >= :from AND date <= :to " +
            "ORDER BY substr(date, 5, length(date)), minute")
    public abstract List<AlphaESSTransformedData> getAlphaESSTransformedData(String systemSN, String from, String to);

    @Query("SELECT AVG(sub.load) * 12 AS BaseLoad FROM (SELECT load FROM alphaESSTransformedData WHERE load > 0.0 " +
            "AND sysSn = :systemSN AND date >= :from AND date <= :to ORDER BY load " +
            "LIMIT CAST((SELECT COUNT(load) * 0.3 FROM alphaESSTransformedData WHERE load > 0 " +
            "AND sysSn = :systemSN AND date >= :from AND date <= :to) AS INTEGER)) sub")
    public abstract Double getBaseLoad(String systemSN, String from, String to);

    @Query("SELECT (charge - discharge)/charge * 100 " +
            "FROM (SELECT sum(energyCharge) AS charge, sum(energyDischarge) AS discharge FROM alphaESSRawEnergy " +
            "WHERE sysSn = :systemSN)")
    public abstract Double getLosses(String systemSN);

    @Query("SELECT gridCharge AS rate FROM alphaESSRawPower WHERE gridCharge > 0 " +
            "AND sysSn = :systemSN AND CAST (ROUND(cbat) AS INTEGER) >= :low " +
            "AND CAST (ROUND(cbat) AS INTEGER) < :high ORDER BY gridCharge\n")
    public abstract List<Double> getChargeModelInput(String systemSN, int low, int high);

    @Query("SELECT min(cbat) FROM alphaESSRawPower WHERE cbat > 0 AND sysSn = :systemSN GROUP BY uploadTime ORDER BY min(cbat)")
    public abstract List<Double> getDischargeStopInput(String systemSN);

    @Query("SELECT strftime('%s', datetime((strftime('%s', uploadTime) / 300) * 300, 'unixepoch')) " +
            "AS longtime, load, cbat FROM alphaESSRawPower WHERE sysSn = :systemSN AND cbat > 0 ORDER BY longtime")
    public abstract List<MaxCalcRow> getMaxCalcInput(String systemSN);

    @Query("SELECT DISTINCT " +
            "CAST (strftime('%m', uploadTime) AS INTEGER) AS month, " +
            "strftime('%w', uploadTime) AS day_of_week, " +
            "CAST (strftime('%H', uploadTime) AS INTEGER) AS hour, " +
            "cbat, " +
            "CASE " +
            "WHEN gridCharge - load > 0 THEN gridCharge " +
            "WHEN gridCharge - load < -0.0 THEN 1 " +
            "ELSE 0 " +
            "END AS CFG " +
            "FROM alphaESSRawPower WHERE gridCharge - load > -0.1 " +
            "AND uploadTime >= :from AND uploadTime <= :to AND sysSn = :systemSN " +
            "GROUP BY month, day_of_week, hour " +
            "ORDER BY uploadTime, month, day_of_week, hour")
    public abstract List<ScheduleRIInput> getScheduleRIInput(String systemSN, String from , String to);

    @Query("SELECT MAX(date) as latest FROM alphaESSTransformedData WHERE sysSn = :systemSN")
    public abstract String getLatestDateForSn(String systemSN);

    @Query("SELECT MAX(PV) AS kWp FROM (SELECT date, substr(minute,1,2) AS hr, SUM(pv) AS PV " +
            "FROM alphaESSTransformedData WHERE sysSn = :systemSN GROUP BY date, hr)")
    abstract public double getHAPopv(String systemSN);

    @Query("SELECT  MAX(MAX(PV, OUTPUT)) AS inverterMaxPower FROM  ( " +
            "SELECT date, substr(minute,1,2) AS hr, SUM(pv) AS PV, SUM(load + feed) AS OUTPUT, SUM(buy) AS BUY " +
            "FROM alphaESSTransformedData WHERE sysSn = :systemSN AND BUY <= 0 GROUP BY date, hr ORDER BY PV DESC )")
    abstract public double getHAPoinv(String systemSN);
}

