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

package com.tfcode.comparetout.scenario.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.scenario.sim.SimTime;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Unit coverage for {@link PVGISLoader#mapHourlyTo2001Rows} — the PVGIS ingestion mapping (Phase 4b).
 *
 * <p>Pins the canonical-time contract: PVGIS timestamps are UTC (no device-zone conversion, no
 * {@code minusHours(1)} fudge), but PVGIS-SARAH2 stamps the hourly value at {@code :11} past the hour, so the
 * hour is truncated to its top before being expanded into the twelve {@code :00,:05,…,:55} slots. The rows
 * are remapped onto the synthetic 2001 UTC grid, with Feb 29 of a leap source year dropped, so the PV row
 * count stays equal to the load's 105120 and — critically — the millis-keyed PV/load merge aligns
 * row-for-row. An off-grid stamp would land PV on instants the load never has and silently drop all of it.</p>
 */
public class PVGISLoaderMappingTest {

    private static final long PANEL = 7L;
    private static final double TOL = 1e-9;
    private static final double MAGIC_NUMBER = 919821d; // mirrors PVGISLoader.MAGIC_NUMBER

    /** A normal hour expands into twelve 5-minute rows on the 2001 grid, stored as the raw UTC instant. */
    @Test
    public void mapsOneHourToTwelveRowsOnThe2001UtcGrid() {
        // 2020-06-15 09:00 UTC. The year is remapped to 2001; month/day/HH:mm are kept.
        List<PanelData> rows = PVGISLoader.mapHourlyTo2001Rows(PANEL, "20200615:0900", 500d, 2, 0.4);

        assertEquals(12, rows.size());

        int expectedDow = LocalDateTime.of(2001, 6, 15, 9, 0).getDayOfWeek().getValue();
        int expectedDoY = LocalDateTime.of(2001, 6, 15, 9, 0).getDayOfYear();
        double expectedPv = (500d / 12d / MAGIC_NUMBER) * 2 * 0.4;

        for (int i = 0; i < 12; i++) {
            PanelData row = rows.get(i);
            LocalDateTime slot = LocalDateTime.of(2001, 6, 15, 9, 0).plusMinutes(5L * i);
            assertEquals(PANEL, row.getPanelID());
            assertEquals("2001-06-15", row.getDate());
            assertEquals(String.format("%02d:%02d", slot.getHour(), slot.getMinute()), row.getMinute());
            assertEquals(slot.getHour() * 60 + slot.getMinute(), row.getMod());
            assertEquals(expectedDow, row.getDow());
            assertEquals(expectedDoY, row.getDo2001());
            // Stored as the raw UTC instant on the 2001 grid — no zone shift, no hour fudge.
            assertEquals(SimTime.toEpochMillis(slot, ZoneOffset.UTC), (long) row.getMillisSinceEpoch());
            assertEquals(expectedPv, row.getPv(), TOL);
        }
    }

    /** The first slot's stored instant is exactly 2001-06-15T09:00:00Z (the "store UTC" contract). */
    @Test
    public void firstSlotIsTheRawUtcInstant() {
        List<PanelData> rows = PVGISLoader.mapHourlyTo2001Rows(PANEL, "20240615:0900", 500d, 1, 1.0);
        long expected = LocalDateTime.of(2001, 6, 15, 9, 0).atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expected, (long) rows.get(0).getMillisSinceEpoch());
        assertEquals(540, rows.get(0).getMod()); // 09:00
    }

    /**
     * Regression for the "no PV in the simulation" bug: real PVGIS-SARAH2 stamps the hour at {@code :11}
     * (e.g. {@code 20190101:0011}). The twelve slots MUST be snapped to the {@code :00,:05,…,:55} grid — not
     * left at {@code :11,:16,…} — or every PV row lands on an instant the (on-the-:00-grid) load never has and
     * the millis merge drops all of it.
     */
    @Test
    public void sarah2ElevenPastTheHourIsSnappedToTheFiveMinuteGrid() {
        List<PanelData> rows = PVGISLoader.mapHourlyTo2001Rows(PANEL, "20190101:0011", 500d, 1, 1.0);

        assertEquals(12, rows.size());
        for (int i = 0; i < 12; i++) {
            PanelData row = rows.get(i);
            int expectedMod = i * 5;                       // 00:00, 00:05, … 00:55 — NOT 00:11, 00:16, …
            assertEquals("slot " + i + " must be on the 5-minute grid", expectedMod, row.getMod());
            assertEquals(0, row.getMod() % 5);             // never an off-grid minute
            LocalDateTime slot = LocalDateTime.of(2001, 1, 1, 0, 0).plusMinutes(5L * i);
            assertEquals(SimTime.toEpochMillis(slot, ZoneOffset.UTC), (long) row.getMillisSinceEpoch());
        }
        // First slot is the top of the hour, not the raw :11 stamp.
        assertEquals(0, rows.get(0).getMod());
    }

    /** An hour that falls entirely on Feb 29 of a leap source year is dropped (2001 is non-leap). */
    @Test
    public void feb29OfALeapYearIsDropped() {
        // 2020 is a leap year; 12:00–12:55 are all 29 Feb, so every slot is skipped.
        List<PanelData> rows = PVGISLoader.mapHourlyTo2001Rows(PANEL, "20200229:1200", 500d, 1, 1.0);
        assertTrue("All Feb 29 slots must be dropped", rows.isEmpty());
    }

    /** The hour immediately before Feb 29 (28 Feb 23:00) is kept and maps to 2001-02-28. */
    @Test
    public void feb28OfALeapYearIsKept() {
        List<PanelData> rows = PVGISLoader.mapHourlyTo2001Rows(PANEL, "20200228:2300", 500d, 1, 1.0);
        assertEquals(12, rows.size());
        assertEquals("2001-02-28", rows.get(0).getDate());
        assertEquals(23 * 60, rows.get(0).getMod());
        assertEquals(23 * 60 + 55, rows.get(11).getMod());
    }
}
