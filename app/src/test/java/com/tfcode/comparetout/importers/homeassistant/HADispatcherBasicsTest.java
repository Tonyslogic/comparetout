/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.homeassistant;

import static org.junit.Assert.*;

import static java.lang.Thread.sleep;

import com.tfcode.comparetout.importers.TestSecrets;
import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.StatsForPeriodRequest;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HADispatcherBasicsTest {
    private static final String HA_URL = "http://" + TestSecrets.HA_IP + ":8123/api/websocket";

    @Test
    public void testHADispatcherBadToken() throws InterruptedException {
        HADispatcher dispatcher = new HADispatcher(HA_URL, "token");
        assertNotNull(dispatcher);
        dispatcher.start();
        sleep(1000);
        assertFalse(dispatcher.isAuthorized());
        dispatcher.stop();
    }
    @Test
    public void testHADispatcherGoodToken() throws InterruptedException {
        HADispatcher dispatcher = new HADispatcher(HA_URL, TestSecrets.HA_TOKEN);
        assertNotNull(dispatcher);
        dispatcher.start();
        sleep(1000);
        assertTrue(dispatcher.isAuthorized());
        dispatcher.stop();
    }
    @Test
    public void testHADispatcherEnergyPrefs() throws InterruptedException {
        HADispatcher dispatcher = new HADispatcher(HA_URL, TestSecrets.HA_TOKEN);
        assertNotNull(dispatcher);
        dispatcher.start();
        sleep(1000);
        assertTrue(dispatcher.isAuthorized());
        EnergyPrefsRequest request = new EnergyPrefsRequest();
        request.setId(dispatcher.generateId());
        EnergyPrefsResultHandler result = new EnergyPrefsResultHandler();
        dispatcher.sendMessage(request, result);
        sleep(1000);
        assertEquals(5, result.getSensors().size());
        dispatcher.stop();
    }

    @Test
    public void testHADispatcherEnergyStats() throws InterruptedException {
        HADispatcher dispatcher = new HADispatcher(HA_URL, TestSecrets.HA_TOKEN);
        assertNotNull(dispatcher);
        dispatcher.start();
        sleep(1000);
        assertTrue(dispatcher.isAuthorized());
        StatsForPeriodRequest request = getStatsForPeriodRequest();
        request.setStartAndEndTimes(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), dispatcher.generateId());
        dispatcher.sendMessage(request, new StatsForPeriodResultHandler());
        sleep(1000);
    }



    @Test
    public void testHADispatcherEnergyStats5Minutes() throws InterruptedException {
        HADispatcher dispatcher = new HADispatcher(HA_URL, TestSecrets.HA_TOKEN);
        assertNotNull(dispatcher);
        dispatcher.start();
        sleep(1000);
        assertTrue(dispatcher.isAuthorized());
        StatsForPeriodRequest request = getStatsForPeriodRequest();
        request.set5MinutePeriod();
        request.setStartAndEndTimes(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1), dispatcher.generateId());
        dispatcher.sendMessage(request, new StatsForPeriodResultHandler());
        sleep(1000);
    }

    private static StatsForPeriodRequest getStatsForPeriodRequest() {
        List<String> statsToFetch = new ArrayList<>();
        statsToFetch.add("sensor." + TestSecrets.SERIAL.toLowerCase() + "_grid_to_load");
        statsToFetch.add("sensor." + TestSecrets.SERIAL.toLowerCase() + "_solar_to_grid");
        statsToFetch.add("sensor." + TestSecrets.SERIAL.toLowerCase() + "_solar_production");
        statsToFetch.add("sensor." + TestSecrets.SERIAL.toLowerCase() + "_discharge");
        statsToFetch.add("sensor." + TestSecrets.SERIAL.toLowerCase() + "_charge");
        return new StatsForPeriodRequest(statsToFetch);
    }
}