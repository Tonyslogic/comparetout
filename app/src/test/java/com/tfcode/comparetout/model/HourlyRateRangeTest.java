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

import static org.junit.Assert.*;

import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.priceplan.HourlyRateRange;

import org.junit.Before;
import org.junit.Test;

public class HourlyRateRangeTest {

    HourlyRateRange testObject;
    DoubleHolder dh;

    @Before
    public void setUp() {
        dh = new DoubleHolder();
        dh.update(0,2, 10.0);
        dh.update(2,6,8.0);
        dh.update(6,8, 10.0);
        dh.update(8,17, 20.0);
        dh.update(17, 19, 30.0);
        dh.update(19, 23, 20.0);
        dh.update(23,24,10.0);
        testObject = new HourlyRateRange(dh);
    }

    @Test
    public void lookup() {
        assertEquals(10.0, testObject.lookup(0), 0);
        assertEquals(10.0, testObject.lookup(1), 0);
        assertEquals(8.0, testObject.lookup(2), 0);
        assertEquals(30.0, testObject.lookup(18), 0);
        assertEquals(10.0, testObject.lookup(24), 0);
    }

    @Test
    public void getDoubleHolder() {
        assertEquals(
                "[10.0, 10.0, 8.0, 8.0, 8.0, 8.0, 10.0, 10.0, 20.0, 20.0, 20.0, 20.0, " +
                        "20.0, 20.0, 20.0, 20.0, 20.0, 30.0, 30.0, 20.0, 20.0, 20.0, 20.0, 10.0, 10.0]",
                testObject.getDoubleHolder().toString());
    }

    @Test
    public void getRates() {
        assertEquals(7, testObject.getRates().size());
        dh.update(17,19,20.0);
        testObject = new HourlyRateRange(dh);
        assertEquals(5, testObject.getRates().size());
    }
}