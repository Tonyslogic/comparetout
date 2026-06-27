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

package com.tfcode.comparetout.scenario;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.Panel;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit cover for the two pure helpers added for "drive CDS weather dates from a historical PV source":
 * {@link HeatPumpWeatherCache#pvSourcePeriod(List)} and {@link HeatPumpWeatherCache#remapWeatherTo2001(String)}.
 */
public class HeatPumpWeatherCacheTest {

    private static Panel panel(String source, String start, String end) {
        Panel p = new Panel();
        p.setDataSource(source);
        p.setDataStartDate(start);
        p.setDataEndDate(end);
        return p;
    }

    @Test
    public void pvSourcePeriodNullWithoutPanels() {
        assertNull(HeatPumpWeatherCache.pvSourcePeriod(null));
        assertNull(HeatPumpWeatherCache.pvSourcePeriod(new ArrayList<>()));
    }

    @Test
    public void pvSourcePeriodIgnoresPvgisReferenceYear() {
        // PVGIS/default panels sit on the 2001 reference year → no real range → keep today's load-grid behaviour.
        List<Panel> panels = Collections.singletonList(panel("PVGIS", "2001-01-01", "2001-12-31"));
        assertNull(HeatPumpWeatherCache.pvSourcePeriod(panels));
    }

    @Test
    public void pvSourcePeriodReturnsHistoricalRange() {
        List<Panel> panels = Collections.singletonList(panel("AlphaESS", "2023-06-01", "2024-05-31"));
        assertArrayEquals(new String[]{"2023-06-01", "2024-05-31"},
                HeatPumpWeatherCache.pvSourcePeriod(panels));
    }

    @Test
    public void pvSourcePeriodSpansMinStartMaxEndAcrossImportPanels() {
        // A PVGIS-default panel is ignored; the two import strings define the covering window.
        List<Panel> panels = Arrays.asList(
                panel("PVGIS", "2001-01-01", "2001-12-31"),
                panel("AlphaESS", "2023-03-01", "2023-12-31"),
                panel("AlphaESS", "2023-01-01", "2023-11-30"));
        assertArrayEquals(new String[]{"2023-01-01", "2023-12-31"},
                HeatPumpWeatherCache.pvSourcePeriod(panels));
    }

    @Test
    public void remapStampsRowsToReferenceYearPreservingMonthDayTime() {
        // valid_time deliberately NOT the first column — it must be resolved by name.
        String csv = "u10,valid_time,t2m,v10\n"
                + "1.0,2023-07-15 13:00:00,290.0,2.0\n"
                + "1.5,2024-05-31 23:00:00,288.0,2.5\n";
        String out = HeatPumpWeatherCache.remapWeatherTo2001(csv);
        assertTrue(out.contains("1.0,2001-07-15 13:00:00,290.0,2.0"));
        assertTrue(out.contains("1.5,2001-05-31 23:00:00,288.0,2.5"));
        assertFalse(out.contains("2023"));
        assertFalse(out.contains("2024"));
    }

    @Test
    public void remapDropsLeapDayForNonLeapReferenceYear() {
        String csv = "valid_time,u10,v10,t2m\n"
                + "2024-02-28 12:00:00,1,1,280\n"
                + "2024-02-29 12:00:00,1,1,281\n"
                + "2024-03-01 12:00:00,1,1,282\n";
        String out = HeatPumpWeatherCache.remapWeatherTo2001(csv);
        assertTrue(out.contains("2001-02-28 12:00:00"));
        assertTrue(out.contains("2001-03-01 12:00:00"));
        assertFalse(out.contains("02-29")); // 29 Feb dropped (2001 is non-leap)
    }
}
