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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataMassagerTest {

    GetOneDayPowerResponse powerResponseMissing = null;
    GetOneDayPowerResponse powerResponseDuplicate = null;
    GetOneDayPowerResponse powerResponseMarDST = null;
    GetOneDayPowerResponse powerResponseOctDST = null;
    GetOneDayPowerResponse powerResponse220911 = null;

    GetOneDayEnergyResponse energyResponse = null;
    GetOneDayEnergyResponse energyResponse220911 = null;
    private static final String FILENAME_ODPR_220911 = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayPowerResponse.220911.json";
    private static final String FILENAME_ODPR_DST_MAR = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayPowerResponseMarDST.json";
    private static final String FILENAME_ODPR_DST_OCT = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayPowerResponseOctDST.json";
    private static final String FILENAME_ODPR_DUPL = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayPowerResponseJan5.json";
    private static final String FILENAME_ODPR_MISSING = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayPowerResponse.json";

    private static final String FILENAME_ODER = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayEnergyResponse.json";
    private static final String FILENAME_ODER_290911 = ".\\src\\test\\java\\com\\tfcode\\comparetout\\importers\\alphaess\\OneDayEnergyResponse.220911.json";

    @Before
    public void setup() throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(FILENAME_ODPR_MISSING));
        Type type = new TypeToken<GetOneDayPowerResponse>() {}.getType();
        powerResponseMissing = new Gson().fromJson(br, type);

        br =  new BufferedReader(new FileReader(FILENAME_ODPR_DUPL));
        powerResponseDuplicate = new Gson().fromJson(br, type);

        br =  new BufferedReader(new FileReader(FILENAME_ODPR_DST_MAR));
        powerResponseMarDST = new Gson().fromJson(br, type);

        br =  new BufferedReader(new FileReader(FILENAME_ODPR_DST_OCT));
        powerResponseOctDST = new Gson().fromJson(br, type);

        br =  new BufferedReader(new FileReader(FILENAME_ODPR_220911));
        powerResponse220911 = new Gson().fromJson(br, type);


        BufferedReader br2 = new BufferedReader(new FileReader(FILENAME_ODER));
        Type type2 = new TypeToken<GetOneDayEnergyResponse>() {}.getType();
        energyResponse = new Gson().fromJson(br2, type2);
        br2 = new BufferedReader(new FileReader(FILENAME_ODER_290911));
        energyResponse220911 = new Gson().fromJson(br2, type2);
    }

    @Test
    public void duplicatesInPowerResponse() {
        Set<String> check = new HashSet<>();
        boolean ck = true;
        for (GetOneDayPowerResponse.DataItem item : powerResponseDuplicate.data) {
            ck = check.add(item.uploadTime);
            if (!ck) break;
        }
        assertFalse(ck);
    }

    @Test
    public void oneDayDataInFiveMinuteIntervalsTest() {
        assertEquals(261, powerResponseMissing.data.size());
        List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerResponseMissing);
        assertEquals(261, points.size());
        Map<Long, FiveMinuteEnergies> fixed = DataMassager.oneDayDataInFiveMinuteIntervals(points);
        assertEquals(288, fixed.size());

//        for (Map.Entry<Long, Pair<Double, Double>> entry : fixed.entrySet()) {
//            Long key = entry.getKey();
//            Pair<Double, Double> value = entry.getValue();
//
//            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
//
//
//            System.out.println("Key: " + sdf.format(new Date(key)) + " pv: " + value.first + " l: " + value.second);
//            System.out.println("Value (pv): " + value.first);
//            System.out.println("Value (load): " + value.second);
//            System.out.println("---------------");
//        }
    }

    @Test
    public void massageAddMidnight() {
        List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerResponse220911);
        checkMassaged(points, energyResponse220911);
    }

    @Test
    public void massageTestMissing() {
        List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerResponseMissing);
        checkMassaged(points, energyResponse);
    }

    @Test
    public void massageTestDuplicate() {
        List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerResponseDuplicate);
        checkMassaged(points, energyResponse);
    }

    @Test
    public void massageTestOctDST() {
        List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerResponseOctDST);
        checkMassaged(points, energyResponse);
    }

    @Test
    public void massageTestMarDST() {
        List<DataMassager.DataPoint> points = DataMassager.getDataPointsForPowerResponse(powerResponseMarDST);
        checkMassaged(points, energyResponse);
    }

    private void checkMassaged(List<DataMassager.DataPoint> points, GetOneDayEnergyResponse energyResponse) {
        Map<Long, FiveMinuteEnergies> fixed = DataMassager.oneDayDataInFiveMinuteIntervals(points);
        double ePV = energyResponse.data.epv;
        double eLoad = (ePV - energyResponse.data.eOutput) + energyResponse.data.eInput;
        double eFeed = energyResponse.data.eOutput;
        // Unitize and scale power
        Map<Long, FiveMinuteEnergies> massaged = DataMassager.massage(fixed, ePV, eLoad, eFeed);
        assertEquals(288, massaged.size());

        double totalPowerPV = 0;
        double totalPowerLoad = 0;
        double totalPowerFeed = 0;
        for (Map.Entry<Long, FiveMinuteEnergies> entry : massaged.entrySet()) {
            Long key = entry.getKey();
            FiveMinuteEnergies value = entry.getValue();
            totalPowerPV += entry.getValue().pv;
            totalPowerLoad += entry.getValue().load;
            totalPowerFeed += entry.getValue().feed;
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            System.out.println("Key: " + sdf.format(new Date(key)) + " pv: " + value.pv + " l: " + value.load + " f: " + value.feed);
//            System.out.println("Key: " + key + " pv: " + value.first + " l: " + value.second);
        }
        assertEquals(ePV, totalPowerPV, 0.1);
        assertEquals(eLoad, totalPowerLoad, 0.1);
        assertEquals(eFeed, totalPowerFeed, 0.1);
        checkForDuplicates(massaged);
    }

    private void checkForDuplicates(Map<Long, FiveMinuteEnergies> massaged) {
        Set<String> check = new HashSet<>();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        boolean ck = true;
        for (Map.Entry<Long, FiveMinuteEnergies> entry : massaged.entrySet()) {
            ck = check.add(sdf.format(new Date(entry.getKey())));
            if (!ck) {
                System.out.println(sdf.format(new Date(entry.getKey())));
                System.out.println(massaged.size());
                break;
            }
        }
        assertTrue(ck);
    }
}