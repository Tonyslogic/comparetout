/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.scenario.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * v16 date windows in the HW/EV schedule lookups (dynamic-tariff Phase 7):
 * the window limits the schedule to its MM/DD range, the date-blind legacy
 * overloads ignore it, and the defaults reproduce the pre-v16 exclusive-end
 * hour semantics exactly.
 */
public class ComponentDateWindowTest {

    private static EVCharge summerCharge() {
        EVCharge charge = new EVCharge();
        charge.setBegin(2);
        charge.setEnd(6);
        charge.setStartDate("06/10");
        charge.setEndDate("06/20");
        return charge;
    }

    @Test
    public void evChargeRespectsTheDateWindow() {
        List<EVCharge> charges = Collections.singletonList(summerCharge());
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 180));
        assertNull("before the window",
                EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 5, 180));
        assertNull("after the window",
                EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 25, 180));
        assertNull("wrong month",
                EvChargeComponent.scheduledChargeOrNull(charges, 1, 7, 15, 180));
    }

    @Test
    public void legacyOverloadStaysDateBlind() {
        List<EVCharge> charges = Collections.singletonList(summerCharge());
        // The 4-arg lookup has no interval date, so the window cannot apply.
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 180));
    }

    @Test
    public void defaultsKeepTheLegacyExclusiveEndHour() {
        EVCharge charge = new EVCharge();
        charge.setBegin(2);
        charge.setEnd(6);
        List<EVCharge> charges = Collections.singletonList(charge);
        assertNotNull("02:00 starts the window",
                EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 120));
        assertNotNull("05:55 is inside",
                EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 355));
        assertNull("06:00 is out — the end hour is EXCLUDED for EV/HW",
                EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 360));
    }

    @Test
    public void minuteWindowOverridesTheLegacyHours() {
        EVCharge charge = summerCharge();
        charge.setBeginMinute(150);
        charge.setEndMinute(250);
        List<EVCharge> charges = Collections.singletonList(charge);
        assertNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 120));
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 150));
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 245));
        assertNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 6, 15, 250));
    }

    @Test
    public void hwScheduleAndEvDivertShareTheSemantics() {
        HWSchedule hws = new HWSchedule();
        hws.setBegin(10);
        hws.setEnd(12);
        hws.setStartDate("11/01");
        hws.setEndDate("12/31");
        List<HWSchedule> hwList = Collections.singletonList(hws);
        assertTrue(HwComponent.isHotWaterHeatingScheduled(hwList, 1, 11, 15, 630));
        assertFalse("October is outside",
                HwComponent.isHotWaterHeatingScheduled(hwList, 1, 10, 15, 630));
        assertTrue("legacy overload is date-blind",
                HwComponent.isHotWaterHeatingScheduled(hwList, 1, 10, 630));

        EVDivert evd = new EVDivert();
        evd.setBegin(9);
        evd.setEnd(17);
        evd.setStartDate("03/01");
        evd.setEndDate("03/15");
        List<EVDivert> evdList = Collections.singletonList(evd);
        assertNotNull(EvDivertComponent.scheduledDivertOrNull(evdList, 1, 3, 10, 600));
        assertNull(EvDivertComponent.scheduledDivertOrNull(evdList, 1, 3, 20, 600));
    }

    @Test
    public void malformedDatesFallBackToTheFullYear() {
        assertTrue(ScheduleDateWindow.contains("garbage", "12/31", 6, 15));
        assertTrue(ScheduleDateWindow.contains("01/01", "not-a-date", 6, 15));
        assertEquals(-1, ScheduleDateWindow.mmddKey("nope"));
        assertEquals(615, ScheduleDateWindow.mmddKey("06/15"));
    }

    @Test
    public void dayOfMonthUtcMatchesTheCalendar() {
        long millis = LocalDate.of(2001, 6, 15).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli() + 13 * 3_600_000L;
        assertEquals(15, ScheduleDateWindow.dayOfMonthUtc(millis));
    }
}
