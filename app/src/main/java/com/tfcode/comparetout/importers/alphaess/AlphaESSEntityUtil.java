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

package com.tfcode.comparetout.importers.alphaess;

import android.annotation.SuppressLint;

import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class AlphaESSEntityUtil {

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat HH_MM_FORMAT = new SimpleDateFormat("HH:mm");
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getDefault());
        HH_MM_FORMAT.setTimeZone(TimeZone.getDefault());
    }


    public static List<AlphaESSRawPower> getPowerRowsFromJson (GetOneDayPowerResponse odpr) {
        List<AlphaESSRawPower> entities = new ArrayList<>();
        for (GetOneDayPowerResponse.DataItem entry : odpr.data) {
            AlphaESSRawPower entity = new AlphaESSRawPower();
            entity.setSysSn(entry.sysSn);
            entity.setUploadTime(entry.uploadTime);
            entity.setPpv(entry.ppv);
            entity.setLoad(entry.load);
            entity.setCbat(entry.cbat);
            entity.setFeedIn(entry.feedIn);
            entity.setGridCharge(entry.gridCharge);
            entity.setPchargingPile(entry.pchargingPile);
            entities.add(entity);
        }
        return entities;
    }

    public static AlphaESSRawPower getPowerRowFromJsonDataItem (GetOneDayPowerResponse.DataItem entry) {
        AlphaESSRawPower entity = new AlphaESSRawPower();
        entity.setSysSn(entry.sysSn);
        entity.setUploadTime(entry.uploadTime);
        entity.setPpv(entry.ppv);
        entity.setLoad(entry.load);
        entity.setCbat(entry.cbat);
        entity.setFeedIn(entry.feedIn);
        entity.setGridCharge(entry.gridCharge);
        entity.setPchargingPile(entry.pchargingPile);
        return entity;
    }
    public static AlphaESSRawEnergy getEnergyRowFromJson (GetOneDayEnergyResponse oder) {
        AlphaESSRawEnergy entity = new AlphaESSRawEnergy();
        entity.setSysSn(oder.data.sysSn);
        entity.setTheDate(oder.data.theDate);
        entity.setEnergyCharge(oder.data.eCharge);
        entity.setEnergypv(oder.data.epv);
        entity.setEnergyOutput(oder.data.eOutput);
        entity.setEnergyInput(oder.data.eInput);
        entity.setEnergyGridCharge(oder.data.eGridCharge);
        entity.setEnergyDischarge(oder.data.eDischarge);
        entity.setEnergyChargingPile(oder.data.eChargingPile);
        return entity;
    }

    public static AlphaESSRawEnergy getEnergyRowFromJsonDataItem (GetOneDayEnergyResponse.DataItem data) {
        AlphaESSRawEnergy entity = new AlphaESSRawEnergy();
        entity.setSysSn(data.sysSn);
        entity.setTheDate(data.theDate);
        entity.setEnergyCharge(data.eCharge);
        entity.setEnergypv(data.epv);
        entity.setEnergyOutput(data.eOutput);
        entity.setEnergyInput(data.eInput);
        entity.setEnergyGridCharge(data.eGridCharge);
        entity.setEnergyDischarge(data.eDischarge);
        entity.setEnergyChargingPile(data.eChargingPile);
        return entity;
    }

    public static List<AlphaESSTransformedData> getTransformedDataRows (Map<Long, FiveMinuteEnergies> rows, String sysSN) {
        return getTransformedDataRows(rows, null, sysSN);
    }

    /**
     * v2 overload: also populates the per-interval flow decomposition
     * (pv2load, pv2bat, pv2grid, bat2load, bat2grid, grid2load, grid2bat,
     * batChargeIn, batDischargeOut) and {@code evActual} from
     * {@code evByInterval} (may be null when EV data is unavailable).
     */
    public static List<AlphaESSTransformedData> getTransformedDataRows (
            Map<Long, FiveMinuteEnergies> rows,
            Map<Long, Double> evByInterval,
            String sysSN) {
        List<AlphaESSTransformedData> entities = new ArrayList<>();
        for (Map.Entry<Long, FiveMinuteEnergies> entry : rows.entrySet()) {
            AlphaESSTransformedData entity = new AlphaESSTransformedData();
            entity.setSysSn(sysSN);
            Date date = new Date(entry.getKey());
            entity.setDate(DATE_FORMAT.format(date));
            entity.setMinute(HH_MM_FORMAT.format(date));
            double pv = entry.getValue().pv.isNaN() ? 0D : entry.getValue().pv;
            double load = entry.getValue().load.isNaN() ? 0D : entry.getValue().load;
            double feed = entry.getValue().feed.isNaN() ? 0D : entry.getValue().feed;
            double buy = entry.getValue().buy.isNaN() ? 0D : entry.getValue().buy;
            entity.setPv(pv);
            entity.setLoad(load);
            entity.setFeed(feed);
            entity.setBuy(buy);
            double charge = entry.getValue().charge.isNaN() ? 0D : entry.getValue().charge;
            // Assume losses of 10% when discharging
            // TODO make this configurable in the importer
            entity.setCharge(charge > 0D ? charge : charge * 0.9);
            entity.setMillisSinceEpoch(date.getTime());

            // v2 flow decomposition — exact, energy-balance-preserving allocation.
            AlphaESSFlowDecomposer.FlowDecomposition flows =
                    AlphaESSFlowDecomposer.decompose(pv, load, feed, buy);
            entity.setPv2load(flows.pv2load);
            entity.setPv2bat(flows.pv2bat);
            entity.setPv2grid(flows.pv2grid);
            entity.setBat2load(flows.bat2load);
            entity.setBat2grid(flows.bat2grid);
            entity.setGrid2load(flows.grid2load);
            entity.setGrid2bat(flows.grid2bat);
            entity.setBatChargeIn(flows.batChargeIn);
            entity.setBatDischargeOut(flows.batDischargeOut);

            if (evByInterval != null) {
                Double ev = evByInterval.get(entry.getKey());
                if (ev != null && !ev.isNaN()) entity.setEvActual(ev);
            }

            entities.add(entity);
        }
        return entities;
    }
}
