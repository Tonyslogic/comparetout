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

package com.tfcode.comparetout;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.google.gson.Gson;
import com.tfcode.comparetout.model.json.scenario.pgvis.PvGISData;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Real-network smoke test that pulls a year of hourly irradiance data
 * from PVGIS (re.jrc.ec.europa.eu) for a fixed Dublin location and verifies
 * the response parses into the {@link PvGISData} structure with the
 * expected ~8760 hourly rows. Uses the same URL pattern as PVGISActivity.
 *
 * Network-dependent; marked @LargeTest so it can be excluded from
 * fast suites if PVGIS is rate-limiting or down.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class SmokePVGISTest {

    // Dublin city centre; mid-EU coverage in the PVGIS-SARAH2 database.
    private static final double LAT = 53.349;
    private static final double LON = -6.260;
    private static final int SLOPE = 35;     // tilt (deg from horizontal)
    private static final int AZIMUTH = 180;  // south-facing

    // Mirrors PVGISActivity's URL composition (api/v5_2/seriescalc, SARAH2, 2019).
    private static final String U1 = "https://re.jrc.ec.europa.eu/api/v5_2/seriescalc?lat=";
    private static final String U2 = "&lon=";
    private static final String U3 = "&raddatabase=PVGIS-SARAH2&browser=1&outputformat=json"
            + "&userhorizon=&usehorizon=1&angle=";
    private static final String U4 = "&aspect=";
    private static final String U5 = "&startyear=2019&endyear=2019&mountingplace="
            + "&optimalinclination=0&optimalangles=0&js=1&select_database_hourly=PVGIS-SARAH2"
            + "&hstartyear=2019&hendyear=2019&trackingtype=0&hourlyangle=";
    private static final String U6 = "&hourlyaspect=";

    @Test
    public void liveDublinPullParsesIntoExpectedShape() throws Exception {
        String url = U1 + LAT + U2 + LON + U3 + SLOPE + U4 + AZIMUTH + U5 + SLOPE + U6 + AZIMUTH;
        String body = httpGet(url);
        assertNotNull("PVGIS response body must not be null", body);
        assertTrue("response too small to be valid PVGIS payload (got " + body.length() + " bytes)",
                body.length() > 10_000);

        PvGISData parsed = new Gson().fromJson(body, PvGISData.class);
        assertNotNull("PvGISData object must parse", parsed);
        assertNotNull("outputs.hourly section must be present", parsed.hourlies);
        assertNotNull("outputs.hourly.hourly array must be present", parsed.hourlies.hourlies);

        int rows = parsed.hourlies.hourlies.size();
        // A leap-clean year yields 8760 hourly rows; tolerate ±48 (2 days) for PVGIS variance.
        assertTrue("expected ~8760 hourly rows, got " + rows, rows >= 8712 && rows <= 8808);
    }

    private static String httpGet(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);  // PVGIS can be slow on first hit
        try {
            int code = conn.getResponseCode();
            assertTrue("expected HTTP 200 from PVGIS, got " + code, code == 200);
            try (InputStream in = conn.getInputStream();
                 InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(r)) {
                StringBuilder sb = new StringBuilder(65_536);
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } finally {
            conn.disconnect();
        }
    }
}
