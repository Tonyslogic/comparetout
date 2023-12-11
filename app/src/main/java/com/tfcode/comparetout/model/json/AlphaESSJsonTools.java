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

package com.tfcode.comparetout.model.json;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.json.importers.alphaess.AlphaESSExportJsonFile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AlphaESSJsonTools {

    public static String createAlphaESSExportJson(List<AlphaESSRawPower> powerList, List<AlphaESSRawEnergy> energyList) {
        AlphaESSExportJsonFile alphaESSExportJsonFile = new AlphaESSExportJsonFile();
        List<GetOneDayPowerResponse.DataItem> powerJSON = getPowerDataItems(powerList);
        alphaESSExportJsonFile.energy = getEnergyDataItems(energyList);
        alphaESSExportJsonFile.power = powerJSON;

        Type type = new TypeToken<AlphaESSExportJsonFile>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(alphaESSExportJsonFile,type);
    }

    @NonNull
    public static List<GetOneDayEnergyResponse.DataItem> getEnergyDataItems(List<AlphaESSRawEnergy> energyList) {
        List<GetOneDayEnergyResponse.DataItem> energyJSON = new ArrayList<>();
        for (AlphaESSRawEnergy alphaESSRawEnergy : energyList) {
            GetOneDayEnergyResponse.DataItem item = new GetOneDayEnergyResponse.DataItem();
            item.eGridCharge = alphaESSRawEnergy.getEnergyGridCharge();
            item.eInput = alphaESSRawEnergy.getEnergyInput();
            item.eOutput = alphaESSRawEnergy.getEnergyOutput();
            item.eCharge = alphaESSRawEnergy.getEnergyCharge();
            item.sysSn = alphaESSRawEnergy.getSysSn();
            item.eChargingPile = alphaESSRawEnergy.getEnergyChargingPile();
            item.eDischarge = alphaESSRawEnergy.getEnergyDischarge();
            item.epv = alphaESSRawEnergy.getEnergypv();
            item.theDate = alphaESSRawEnergy.getTheDate();
            energyJSON.add(item);
        }
        return energyJSON;
    }

    @NonNull
    public static List<GetOneDayPowerResponse.DataItem> getPowerDataItems(List<AlphaESSRawPower> powerList) {
        List<GetOneDayPowerResponse.DataItem> powerJSON = new ArrayList<>();
        for (AlphaESSRawPower alphaESSRawPower : powerList) {
            GetOneDayPowerResponse.DataItem item = new GetOneDayPowerResponse.DataItem();
            item.cbat = alphaESSRawPower.getCbat();
            item.feedIn = alphaESSRawPower.getFeedIn();
            item.load = alphaESSRawPower.getLoad();
            item.gridCharge = alphaESSRawPower.getGridCharge();
            item.pchargingPile = alphaESSRawPower.getPchargingPile();
            item.ppv = alphaESSRawPower.getPpv();
            item.sysSn = alphaESSRawPower.getSysSn();
            item.uploadTime = alphaESSRawPower.getUploadTime();
            powerJSON.add(item);
        }
        return powerJSON;
    }

}
