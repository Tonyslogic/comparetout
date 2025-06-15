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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Data Access Object for managing AlphaESS solar inverter system data.
 * 
 * This DAO handles three main types of AlphaESS data with complex time-based aggregations:
 * 
 * 1. **Raw Energy Data**: Daily energy totals from the AlphaESS API
 *    - Daily PV generation, battery charge/discharge, grid import/export
 *    - Used for high-level energy accounting and KPI calculations
 * 
 * 2. **Raw Power Data**: High-frequency power measurements (5-minute intervals)
 *    - Real-time power flows for detailed analysis
 *    - Used for battery modeling and charge pattern analysis
 * 
 * 3. **Transformed Data**: Processed 5-minute interval data 
 *    - Normalized format for cost calculations and scenario modeling
 *    - Derived from raw data with additional calculated fields
 * 
 * Key Aggregation Patterns:
 * - Time-based grouping: hour, day-of-year, day-of-week, month, year
 * - SUM aggregations for energy totals (kWh calculations)  
 * - AVG aggregations for typical usage patterns
 * - Complex date/time manipulation using SQLite strftime functions
 * 
 * The DAO supports:
 * - Data import and synchronization with AlphaESS cloud
 * - Time-series analysis for energy pattern identification
 * - Battery performance modeling and optimization
 * - Cost calculation input data preparation
 * - Statistical analysis and KPI generation
 */
@Dao
public abstract class AlphaEssDAO {

    /**
     * Insert or replace raw energy data from AlphaESS API.
     * Uses REPLACE strategy for data synchronization.
     * @param energy Daily energy totals record
     */
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    public abstract void addRawEnergy(AlphaESSRawEnergy energy);

    /**
     * Insert raw power data, ignoring duplicates to handle API retries.
     * @param power List of 5-minute power measurement records
     */
    @Insert (onConflict = OnConflictStrategy.IGNORE)
    public abstract void addRawPower(List<AlphaESSRawPower> power);

    /**
     * Insert or replace transformed data for analysis.
     * @param data List of processed 5-minute interval records
     */
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    public abstract void addTransformedData(List<AlphaESSTransformedData> data);

    /**
     * Remove all AlphaESS data for a specific system serial number.
     * Performs cascading deletion to maintain data consistency.
     * @param systemSN The system serial number to clear
     */
    @Transaction
    public void clearAlphaESSDataForSN(String systemSN) {
        deleteAlphaESSPowerForSN(systemSN);      // 5-minute power data
        deleteAlphaESSEnergyForSN(systemSN);     // Daily energy data  
        deleteAlphaESSTransformedForSN(systemSN); // Processed data
    }

    @Query("DELETE FROM alphaESSTransformedData WHERE sysSn = :systemSN")
    public abstract void deleteAlphaESSTransformedForSN(String systemSN);

    @Query("DELETE FROM alphaESSRawEnergy WHERE sysSn = :systemSN")
    public abstract void deleteAlphaESSEnergyForSN(String systemSN);

    @Query("DELETE FROM alphaESSRawPower WHERE sysSn = :systemSN")
    public abstract void deleteAlphaESSPowerForSN(String systemSN);

    @Transaction
    public void deleteInverterDatesBySN(String sysSN, LocalDateTime selectedStart, LocalDateTime selectedEnd) {
        DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        deleteSomeAlphaESSPowerForSN(sysSN, selectedStart.format(DISPLAY_FORMAT), selectedEnd.format(DISPLAY_FORMAT));
        deleteSomeAlphaESSEnergyForSN(sysSN, selectedStart.format(DISPLAY_FORMAT), selectedEnd.format(DISPLAY_FORMAT));
        deleteSomeAlphaESSTransformedForSN(sysSN,
                selectedStart.format(DISPLAY_FORMAT) + " 00:00:00",
                selectedEnd.format(DISPLAY_FORMAT) + " 23:59:59");
    }

    @Query("DELETE FROM alphaESSTransformedData WHERE sysSn = :systemSN AND date BETWEEN :selectedStart AND :selectedEnd")
    public abstract void deleteSomeAlphaESSTransformedForSN(String systemSN, String selectedStart, String selectedEnd);

    @Query("DELETE FROM alphaESSRawEnergy WHERE sysSn = :systemSN AND theDate BETWEEN :selectedStart AND :selectedEnd")
    public abstract void deleteSomeAlphaESSEnergyForSN(String systemSN, String selectedStart, String selectedEnd);

    @Query("DELETE FROM alphaESSRawPower WHERE sysSn = :systemSN AND uploadTime BETWEEN :selectedStart AND :selectedEnd")
    public abstract void deleteSomeAlphaESSPowerForSN(String systemSN, String selectedStart, String selectedEnd);

    @Query("SELECT sysSn, MIN(theDate) AS start, MAX(theDate) AS finish FROM alphaESSRawEnergy GROUP by sysSn")
    public abstract LiveData<List<InverterDateRange>> loadDateRanges();

    @Query("SELECT sysSn, MIN(date) AS start, MAX(date) AS finish FROM alphaESSTransformedData GROUP by sysSn")
    public abstract LiveData<List<InverterDateRange>> loadESBNHDFDateRanges();

    @Query("SELECT sysSn, MIN(date) AS start, MAX(date) AS finish FROM alphaESSTransformedData GROUP by sysSn")
    public abstract LiveData<List<InverterDateRange>> loadHomeAssistantDateRange();

    @Query("SELECT CASE WHEN EXISTS (SELECT sysSn, theDate FROM alphaESSRawEnergy WHERE sysSn = :sysSn AND theDate = :date) THEN 1 ELSE 0 END AS date_exists")
    public abstract boolean checkSysSnForDataOnDate(String sysSn, String date);

    /**
     * Aggregate energy data by hour of day across a date range.
     * 
     * Query breakdown:
     * - SUM(pv/load/feed/buy) AS PV/LOAD/FEED/BUY: Total energy by category
     * - TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END): Sum positive battery charging
     * - ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)): Sum absolute discharge values
     * - cast(strftime('%H', minute) as INTEGER): Extract hour (0-23) from time string
     * - GROUP BY INTERVAL: Aggregate all data points for each hour
     * 
     * This creates 24 rows (one per hour) showing typical energy flows throughout the day.
     * Used for identifying peak generation/consumption periods and optimizing battery schedules.
     * 
     * @param sysSN System serial number to filter
     * @param from Start date (YYYY-MM-DD format)
     * @param to End date (YYYY-MM-DD format)
     * @return List of hourly energy totals (24 entries, 0-23 hours)
     */
    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%H', minute) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY  INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumHour(String sysSN, String from, String to);

    /**
     * Aggregate energy data by day of year (1-365/366).
     * 
     * Query uses strftime('%j', date) to extract the day of year number.
     * This creates daily totals across the specified date range, useful for:
     * - Identifying seasonal patterns
     * - Finding best/worst performing days
     * - Calculating daily energy balance
     * 
     * @param sysSN System serial number to filter
     * @param from Start date (YYYY-MM-DD format)  
     * @param to End date (YYYY-MM-DD format)
     * @return List of daily energy totals ordered by day of year
     */
    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
    "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOY(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOW(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "strftime('%Y', date) || strftime('%m', date) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumMonth(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumYear(String sysSN, String from, String to);

    /**
     * Calculate average hourly energy patterns across multiple days.
     * 
     * This complex nested query:
     * 1. Inner query: Groups data by year + day-of-year + hour to get daily hourly totals
     * 2. Outer query: Averages these daily totals by hour to find typical hourly patterns
     * 
     * Query structure:
     * - Inner: GROUP BY year, day-of-year, hour -> daily hourly totals
     * - Outer: GROUP BY hour, AVG() -> average patterns by hour
     * 
     * This reveals typical energy flow patterns by hour of day, smoothing out
     * day-to-day variations to show underlying consumption/generation patterns.
     * Essential for battery scheduling optimization.
     * 
     * @param sysSN System serial number to analyze
     * @param from Start date for analysis period
     * @param to End date for analysis period  
     * @return 24 rows showing average hourly energy flows
     */
    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%H', minute) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%j', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgHour(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, INTERVAL FROM ( " +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOY(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%W', date) as integer), INTERVAL ORDER BY INTERVAL" +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOW(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "strftime('%m', date) as INTERVAL" +
            " FROM alphaESSTransformedData WHERE date >= :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY INTERVAL ORDER BY INTERVAL, date) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgMonth(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, " +
            "0 AS PV2BAT, 0 AS PV2LOAD, 0 AS BAT2LOAD, 0 AS GRID2BAT, 0 AS EVSCHEDULE, 0 AS EVDIVERT, 0 AS HWSCHEDULE, 0 AS HWDIVERT," +
            "0 AS BAT2GRID, avg(BAT_CHARGE) AS BAT_CHARGE, avg(BAT_DISCHARGE) AS BAT_DISCHARGE, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, " +
            "TOTAL(CASE WHEN charge > 0 THEN charge ELSE 0 END) AS BAT_CHARGE, ABS(TOTAL(CASE WHEN charge < 0 THEN charge ELSE 0 END)) AS BAT_DISCHARGE, " +
            "cast (strftime('%Y', date) as INTEGER) AS INTERVAL" +
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

    /**
     * Generate comprehensive monthly statistics showing best/worst/average performance.
     * 
     * This highly complex multi-table query creates a monthly summary with:
     * - Total PV generation per month
     * - Best single day performance (with date)
     * - Worst single day performance (with date)  
     * - Monthly average performance
     * 
     * Query structure breakdown:
     * 1. Main query (main): Groups by month, calculates totals, best day, average
     *    - substr(theDate, 3, 5): Extract MM-DD from YYYY-MM-DD date
     *    - SUM(energypv): Total monthly PV generation
     *    - MAX(energypv): Best single day in month
     *    - AVG(energypv): Monthly average daily generation
     * 
     * 2. Worst subquery (worst): Finds minimum daily generation per month
     *    - MIN(energypv): Worst single day in month
     *    - Uses same month grouping as main query
     * 
     * 3. JOIN condition: WHERE worst.bMonth = main.Month
     *    - Combines best/worst data for each month
     * 
     * Result format: Month, Total_kWh, "Best_kWh on DD", "Worst_kWh on DD", Average_kWh
     * 
     * @param from Start date for analysis (YYYY-MM-DD)
     * @param to End date for analysis (YYYY-MM-DD)
     * @param systemSN System serial number to analyze
     * @return Monthly statistics with best/worst day performance
     */
    @Query("SELECT main.Month, " +
            "tot AS 'PV tot (kWh)', " +
            "best || ' on ' || bestday AS 'Best', " +
            "worst.bad || ' on ' || badday AS 'Worst', " +
            "Average " +
            "FROM  " +
            "(SELECT substr(theDate, 3, 5) AS Month, " +
            "SUM(energypv) AS tot, " +
            "MAX(energypv) AS best, " +
            "substr(theDate,9,2) AS bestday, " +
            "ROUND(AVG(energypv) * 100) / 100 AS Average " +
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
            "ROUND(worst.bad, 2) || ' on ' || badday AS 'Worst', " +
            "Average " +
            "FROM  " +
            "(" +
            "SELECT substr(cMonth, 1,5) AS Month, SUM(bad) AS tot, MAX(bad) AS best, badday AS bestday, ROUND(AVG(bad) * 100) / 100 AS Average  FROM ( " +
            "SELECT substr(date, 3) AS cMonth, " +
            "SUM(pv) AS bad, " +
            "substr(date,9,2) AS badday " +
            "FROM alphaESSTransformedData " +
            "WHERE sysSn = :systemSN AND date >= :from AND date <= :to " +
            "GROUP BY cMonth ORDER BY cMonth ) " +
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

    /**
     * Calculate key performance indicators for the solar system.
     * 
     * This query calculates essential solar system metrics:
     * 
     * KPI Calculations:
     * - SC (Self Consumption): ((PV - Export) / PV) * 100
     *   Percentage of generated solar power used directly vs exported
     * 
     * - SS (Self Sufficiency): ((PV - Export) / Load) * 100  
     *   Percentage of electricity needs met by solar vs grid import
     * 
     * - MSS (Max Self Sufficiency): (PV / Load) * 100
     *   Theoretical maximum self-sufficiency if all PV could be used
     * 
     * - PV: Total solar generation (rounded to 2 decimal places)
     * - FEED: Total grid export (rounded to 2 decimal places)
     * 
     * These metrics are fundamental for:
     * - System performance evaluation
     * - Battery sizing decisions  
     * - Economic analysis of solar installations
     * - Comparison between different system configurations
     * 
     * @param from Start date for KPI calculation period
     * @param to End date for KPI calculation period
     * @param systemSN System serial number to analyze
     * @return Single row with all calculated KPIs
     */
    @Query("SELECT ((sum(pv) - sum(feed)) / sum(pv)) * 100 AS SC, " +
            "((sum(pv) - sum(feed)) / sum(load)) * 100 AS SS, " +
            "((sum(pv) / sum(load)) * 100) AS MSS, " +
            "(ROUND(sum(pv) * 100) / 100) AS PV, " +
            "(ROUND(sum(feed) * 100) / 100) FEED " +
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

    @Query("SELECT sysSn, MIN(date) AS start, MAX(date) AS finish FROM alphaESSTransformedData WHERE sysSn = :sysSN")
    public abstract InverterDateRange getDateRange(String sysSN);
}

