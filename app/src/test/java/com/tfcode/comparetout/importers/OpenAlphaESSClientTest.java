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

package com.tfcode.comparetout.importers;

import static org.junit.Assert.*;

import com.tfcode.comparetout.importers.alphaess.AlphaESSException;
import com.tfcode.comparetout.importers.alphaess.OpenAlphaESSClient;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayEnergyResponse;
import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;

import org.junit.Before;
import org.junit.Test;

public class OpenAlphaESSClientTest {

    private OpenAlphaESSClient mClient;

    @Before
    public void setup() {
        mClient = new OpenAlphaESSClient(
                TestSecrets.APPID,
                TestSecrets.SECRET);
        mClient.setSerial(TestSecrets.SERIAL);
    }

    @Test
    public void getOneDayPowerBySn() throws AlphaESSException {
        String queryDate = "2023-08-24";
        GetOneDayPowerResponse power = mClient.getOneDayPowerBySn(queryDate);
        assertNotNull(power);
        assertNotNull(power.data);
        assertEquals(262, power.data.size());
    }

    @Test
    public void getOneDayEnergyBySn() throws AlphaESSException {
        String queryDate = "2023-08-24";
        GetOneDayEnergyResponse energy = mClient.getOneDayEnergyBySn(queryDate);
        assertNotNull(energy);
        assertNotNull(energy.data);
        assertEquals("2023-08-24", energy.data.theDate);
    }

    @Test
    public void badSerial() {
        String queryDate = "2023-08-24";
        mClient.setSerial("AL2342125067171");
        try {
            GetOneDayEnergyResponse energy = mClient.getOneDayEnergyBySn(queryDate);
        } catch (AlphaESSException ae) {
            assertEquals("err.code=6005 err.msg=This appId is not bound to the SN" , ae.getMessage());
        }

    }
}