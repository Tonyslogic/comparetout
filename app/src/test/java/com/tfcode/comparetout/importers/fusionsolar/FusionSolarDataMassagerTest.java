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

package com.tfcode.comparetout.importers.fusionsolar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tfcode.comparetout.importers.fusionsolar.responses.EnergyBalanceResponse;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class FusionSolarDataMassagerTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final String SYS_SN = "FusionSolar-33554678";
    private static final double DELTA = 1e-6;
    private static final LocalDate JAN_DAY = LocalDate.of(2026, 1, 15);
    private static final Gson GSON = new Gson();

    /** The full 288-stamp HH:mm xAxis the portal returns for a day view. */
    private static String xAxis() {
        StringBuilder b = new StringBuilder("[");
        for (int h = 0; h < 24; h++)
            for (int m = 0; m < 60; m += 5) {
                if (b.length() > 1) b.append(',');
                b.append(String.format("\"%02d:%02d\"", h, m));
            }
        return b.append(']').toString();
    }

    /** A 288-length series with `value` at slot indexes [i0,i1] and "--" elsewhere. */
    private static String series(double value, int... liveSlots) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 288; i++) {
            if (i > 0) b.append(',');
            boolean live = false;
            for (int s : liveSlots) if (s == i) live = true;
            b.append(live ? String.valueOf(value) : "\"--\"");
        }
        return b.append(']').toString();
    }

    private static int slotIndex(int hour, int minute) {
        return (hour * 60 + minute) / 5;
    }

    private static EnergyBalanceResponse parse(String dataJson) {
        return GSON.fromJson("{\"success\":true,\"data\":" + dataJson + "}",
                EnergyBalanceResponse.class);
    }

    private static double sum(List<AlphaESSTransformedData> rows,
                              ToDoubleFunction<AlphaESSTransformedData> get) {
        return rows.stream().mapToDouble(get).sum();
    }

    // ── day shape ───────────────────────────────────────────────────────────

    @Test
    public void fullDayHas288SlotsAndMissingSlotsAreZero() {
        int noon = slotIndex(12, 0);
        int noon5 = slotIndex(12, 5);
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(4.0, noon, noon5) + ","
                + "\"usePower\":" + series(1.0, noon, noon5) + ","
                + "\"totalProductPower\":6.0,"
                + "\"totalUsePower\":2.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        assertEquals(288, rows.size());
        assertEquals("2026-01-15", rows.get(0).getDate());
        assertEquals("00:00", rows.get(0).getMinute());
        assertEquals("23:55", rows.get(287).getMinute());
        assertEquals(SYS_SN, rows.get(0).getSysSn());
        assertTrue(rows.get(noon).getPv() > 0);
        assertEquals(0.0, rows.get(0).getPv(), DELTA);
        // PV normalised to the 6 kWh daily total regardless of the 2 live slots.
        assertEquals(6.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        assertEquals(2.0, sum(rows, AlphaESSTransformedData::getLoad), DELTA);
    }

    @Test
    public void dashSamplesAreAbsentNotZero() {
        // Only productPower present; totals cover pv only. All-"--" load means
        // the load fallback (max(0, pv - feed + buy)) applies = pv here.
        int noon = slotIndex(12, 0);
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(5.0, noon) + ","
                + "\"totalProductPower\":5.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        assertEquals(5.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        // No grid series, no load curve → load degenerates to pv.
        assertEquals(5.0, sum(rows, AlphaESSTransformedData::getLoad), DELTA);
        assertEquals(0.0, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
    }

    // ── normalisation makes stored kWh match the portal totals ───────────────

    @Test
    public void explicitGridSeriesAreNormalisedToTotals() {
        int a = slotIndex(9, 0);
        int b = slotIndex(18, 0);
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(3.0, a) + ","
                + "\"usePower\":" + series(2.0, a, b) + ","
                + "\"buyPower\":" + series(1.5, b) + ","
                + "\"ongridPower\":" + series(2.5, a) + ","
                + "\"totalProductPower\":10.0,"
                + "\"totalUsePower\":8.0,"
                + "\"totalBuyPower\":4.0,"
                + "\"totalOngridPower\":6.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        assertEquals(10.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        assertEquals(8.0, sum(rows, AlphaESSTransformedData::getLoad), DELTA);
        assertEquals(4.0, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
        assertEquals(6.0, sum(rows, AlphaESSTransformedData::getFeed), DELTA);
    }

    /**
     * The dynamic sign pairing must key on magnitude, not field name: even if
     * the "buy"-named series actually carries the larger (feed) flow, pairing
     * with the larger daily total keeps the stored buy/feed correct.
     */
    @Test
    public void gridPairingFollowsMagnitudeNotName() {
        int a = slotIndex(9, 0);
        int b = slotIndex(18, 0);
        // buyPower curve is LARGER, but the buy TOTAL is smaller than feed.
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(3.0, a) + ","
                + "\"buyPower\":" + series(9.0, a) + ","      // larger curve
                + "\"ongridPower\":" + series(1.0, b) + ","   // smaller curve
                + "\"totalProductPower\":5.0,"
                + "\"totalBuyPower\":2.0,"                     // smaller total
                + "\"totalOngridPower\":7.0}";                 // larger total

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        // Larger curve ↔ larger total: the 9.0 curve carries feed (7 kWh),
        // the 1.0 curve carries buy (2 kWh).
        assertEquals(2.0, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
        assertEquals(7.0, sum(rows, AlphaESSTransformedData::getFeed), DELTA);
    }

    @Test
    public void batteryStoredSignedChargeMinusDischarge() {
        int a = slotIndex(10, 0);
        int b = slotIndex(20, 0);
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(4.0, a) + ","
                + "\"usePower\":" + series(2.0, a) + ","
                + "\"chargePower\":" + series(3.0, a) + ","
                + "\"dischargePower\":" + series(2.0, b) + ","
                + "\"totalProductPower\":4.0,"
                + "\"totalUsePower\":2.0,"
                + "\"totalChargePower\":5.0,"
                + "\"totalDischargePower\":3.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        // Signed: +5 charge, -3 discharge → net +2 across the day.
        assertEquals(2.0, sum(rows, AlphaESSTransformedData::getCharge), DELTA);
    }

    // ── grid derivation fallback ─────────────────────────────────────────────

    @Test
    public void gridDerivedFromBalanceWhenNoExplicitSeries() {
        // No buy/feed series. load - pv (no battery) → net grid per slot.
        // One slot: load 2, pv 5 → net = -3 → feed 3, buy 0 (after normalise).
        int a = slotIndex(11, 0);
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(5.0, a) + ","
                + "\"usePower\":" + series(2.0, a) + ","
                + "\"totalProductPower\":5.0,"
                + "\"totalUsePower\":2.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        assertEquals(0.0, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
        assertEquals(3.0, sum(rows, AlphaESSTransformedData::getFeed), DELTA);
    }

    // ── DST + empty handling ─────────────────────────────────────────────────

    @Test
    public void springForwardDayHas276Slots() {
        // Europe/Dublin springs forward 2026-03-29 (23-hour day).
        LocalDate dst = LocalDate.of(2026, 3, 29);
        int a = slotIndex(12, 0);
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(4.0, a) + ","
                + "\"totalProductPower\":4.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, dst, DUBLIN, parse(data));

        assertEquals(276, rows.size());
        assertEquals(4.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
    }

    @Test
    public void allDashDayStoresNothing() {
        String data = "{"
                + "\"xAxis\":" + xAxis() + ","
                + "\"productPower\":" + series(0.0) + ","  // no live slots
                + "\"totalProductPower\":0.0}";

        List<AlphaESSTransformedData> rows =
                FusionSolarDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN, parse(data));

        assertTrue(rows.isEmpty());
    }

    @Test
    public void dnNormalisationStripsNePrefix() {
        assertEquals("FusionSolar-33554678", FusionSolarDataMassager.sysSnFor("NE=33554678"));
        assertEquals("FusionSolar-33554678", FusionSolarDataMassager.sysSnFor("33554678"));
    }
}
