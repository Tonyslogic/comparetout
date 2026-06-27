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

package com.tfcode.comparetout.model.json;

import static org.junit.Assert.assertEquals;

import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.scenario.Panel;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** DB-v11 PV provenance: entity → JSON → entity preserves source + range, and old JSON defaults to 2001. */
public class PanelJsonTest {

    @Test
    public void roundTripPreservesProvenance() {
        Panel panel = new Panel();
        panel.setPanelName("String-1");
        panel.setDataSource("AlphaESS");
        panel.setDataStartDate("2023-06-01");
        panel.setDataEndDate("2024-05-31");

        List<PanelJson> json = JsonTools.createPanelListJson(Collections.singletonList(panel));
        Panel back = JsonTools.createPanelList(json).get(0);

        assertEquals("AlphaESS", back.getDataSource());
        assertEquals("2023-06-01", back.getDataStartDate());
        assertEquals("2024-05-31", back.getDataEndDate());
    }

    @Test
    public void oldImportDefaultsToReferenceYear() {
        // A scenario JSON exported before v11 has no PV-source fields; createPanel must default them so the
        // CDS weather period and cache key stay on the 2001 reference year (byte-identical to pre-v11).
        PanelJson pj = new PanelJson();
        pj.panelCount = 7;
        pj.panelkWp = 325;
        pj.azimuth = 136;
        pj.slope = 24;
        pj.latitude = 53.490;
        pj.longitude = -10.015;
        pj.inverter = "AlphaESS";
        pj.mppt = 1;
        pj.panelName = "String-1";
        pj.optimized = false;
        // dataSource / dataStartDate / dataEndDate intentionally left null (older payload)

        Panel back = JsonTools.createPanel(pj);

        assertEquals("PVGIS", back.getDataSource());
        assertEquals("2001-01-01", back.getDataStartDate());
        assertEquals("2001-12-31", back.getDataEndDate());
    }
}
