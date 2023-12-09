/*
 * Copyright (c) 2023. Tony Finnerty
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

import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.importers.alphaess.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.InverterDateRange;

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

    @Query("SELECT CASE WHEN EXISTS (SELECT sysSn, theDate FROM alphaESSRawEnergy WHERE sysSn = :sysSn AND theDate = :date) THEN 1 ELSE 0 END AS date_exists")
    public abstract boolean checkSysSnForDataOnDate(String sysSn, String date);
    /**
     * -- SUM HOUR
     * SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%H', minute) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY  INTERVAL ORDER BY INTERVAL
     *
     * -- SUM DOY | DAY
     * SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%j', date) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY INTERVAL ORDER BY INTERVAL
     *
     * -- SUM DOW
     * SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%w', date) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY INTERVAL ORDER BY INTERVAL
     *
     * -- SUM MONTH
     * SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%m', date) as integer) as INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY INTERVAL ORDER BY INTERVAL, date
     *
     * -- SUM YEAR
     * SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%Y', date) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY INTERVAL ORDER BY INTERVAL
     *
     *
     * -- AVG HOUR
     * SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (
     *   SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%H', minute) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%j', date) as integer), INTERVAL ORDER BY INTERVAL
     * ) GROUP BY INTERVAL
     *
     * -- AVG DOW
     * SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (
     *   SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%w', date) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%W', date) as integer), INTERVAL ORDER BY INTERVAL
     * ) GROUP BY INTERVAL
     *
     * -- AVG DOY | DAY
     * SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (
     *   SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%j', date) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL
     * ) GROUP BY INTERVAL
     *
     * -- AVG MONTH
     * SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (
     *   SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%m', date) as integer) as INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL, date
     * ) GROUP BY INTERVAL
     *
     * -- AVG YEAR
     * SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (
     *   SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%Y', date) as INTEGER) AS INTERVAL
     *   FROM alphaESSTransformedData WHERE date > '2020-11-15' AND date <= '2024-11-20' GROUP BY INTERVAL ORDER BY INTERVAL )
     */
    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%H', minute) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY  INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumHour(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
    "FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOY(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumDOW(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, strftime('%Y', date) || strftime('%m', date) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumMonth(String sysSN, String from, String to);

    @Query("SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%Y', date) as INTEGER) AS INTERVAL " +
            "FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL")
    public abstract List<IntervalRow> sumYear(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%H', minute) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%j', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgHour(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM ( " +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%j', date) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY cast (strftime('%Y', date) as integer), INTERVAL ORDER BY INTERVAL " +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOY(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%w', date) as INTEGER) AS INTERVAL " +
            " FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY cast (strftime('%Y', date) as integer), cast (strftime('%W', date) as integer), INTERVAL ORDER BY INTERVAL" +
            " ) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgDOW(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, strftime('%m', date) as INTERVAL" +
            " FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN " +
            " GROUP BY INTERVAL ORDER BY INTERVAL, date) GROUP BY INTERVAL")
    public abstract List<IntervalRow> avgMonth(String sysSN, String from, String to);

    @Query("SELECT avg(PV) AS PV, AVG(LOAD) AS LOAD, AVG(FEED) AS FEED, AVG(BUY) AS BUY, INTERVAL FROM (" +
            " SELECT sum(pv) as PV, sum(load) AS LOAD, sum(feed) AS FEED, sum(buy) AS BUY, cast (strftime('%Y', date) as INTEGER) AS INTERVAL" +
            " FROM alphaESSTransformedData WHERE date > :from AND date <= :to AND sysSn = :sysSN GROUP BY INTERVAL ORDER BY INTERVAL )")
    public abstract List<IntervalRow> avgYear(String sysSN, String from, String to);
}

