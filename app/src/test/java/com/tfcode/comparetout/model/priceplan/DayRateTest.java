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

package com.tfcode.comparetout.model.priceplan;

import static org.junit.Assert.*;

import com.tfcode.comparetout.model.priceplan.DayRate;

import org.junit.Before;
import org.junit.Test;

public class DayRateTest {

    DayRate testObjectDefault;

    @Before
    public void setUp() {
        testObjectDefault = new DayRate();
    }

    @Test
    public void getMonthDay() {
        assertArrayEquals(null, new int[]{12,31}, testObjectDefault.getMonthDay("12/31"));
        assertThrows(NumberFormatException.class, () -> testObjectDefault.getMonthDay("12-31"));
        assertThrows(NumberFormatException.class, () -> testObjectDefault.getMonthDay("12/3l"));
        assertThrows(NumberFormatException.class, () -> testObjectDefault.getMonthDay(""));
    }

    @Test
    public void getKey() {
        assertEquals("01/01,12/31", testObjectDefault.getKey());
    }

    @Test
    public void get2001DateRange() {
        assertEquals(1, testObjectDefault.get2001DateRange()[0].getDayOfYear());
        assertEquals(365, testObjectDefault.get2001DateRange()[1].getDayOfYear());
    }

    @Test
    public void validate() {
        // Valid
        assertEquals(0, testObjectDefault.validate());
        // Bad format
        testObjectDefault.setStartDate("21-01");
        assertEquals(DayRate.DR_BAD_START, testObjectDefault.validate());
        testObjectDefault.setStartDate("01/01");
        testObjectDefault.setEndDate("01-01");
        assertEquals(DayRate.DR_BAD_END, testObjectDefault.validate());
        // Start after end
        testObjectDefault.setStartDate("12/31");
        testObjectDefault.setEndDate("01/01");
        assertEquals(DayRate.DR_END_BEFORE_START, testObjectDefault.validate());
    }
}