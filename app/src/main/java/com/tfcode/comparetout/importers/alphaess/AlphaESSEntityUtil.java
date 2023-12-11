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

public class AlphaESSEntityUtil {

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat HH_MM_FORMAT = new SimpleDateFormat("HH:mm");
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

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
        List<AlphaESSTransformedData> entities = new ArrayList<>();
        for (Map.Entry<Long, FiveMinuteEnergies> entry : rows.entrySet()) {
            AlphaESSTransformedData entity = new AlphaESSTransformedData();
            entity.setSysSn(sysSN);
            Date date = new Date(entry.getKey());
            entity.setDate(DATE_FORMAT.format(date));
            entity.setMinute(HH_MM_FORMAT.format(date));
            entity.setPv(entry.getValue().pv);
            entity.setLoad(entry.getValue().load);
            entity.setFeed(entry.getValue().feed);
            entity.setBuy(entry.getValue().buy);
            entities.add(entity);
        }
        return entities;
    }
}
