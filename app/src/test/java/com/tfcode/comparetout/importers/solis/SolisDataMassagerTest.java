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

package com.tfcode.comparetout.importers.solis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.importers.solis.responses.StationDayEnergyResponse;
import com.tfcode.comparetout.importers.solis.responses.StationDayResponse;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class SolisDataMassagerTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final String SYS_SN = "Solis-1298491919448631809";
    private static final double DELTA = 1e-9;

    /** A normal (non-DST) day with UTC==local: slot arithmetic is easy to read. */
    private static final LocalDate JAN_DAY = LocalDate.of(2026, 1, 15);

    private static long millisAt(LocalDate day, int hour, int minute, int second) {
        return day.atTime(hour, minute, second).atZone(DUBLIN).toInstant().toEpochMilli();
    }

    private static StationDayResponse sample(long millis, double pv, double load,
                                             double gridZheng, double gridFu,
                                             double batZheng, double batFu) {
        StationDayResponse s = new StationDayResponse();
        s.time = millis;
        s.power = pv;
        s.powerStr = "kW";
        s.familyLoadPower = load;
        s.familyLoadPowerStr = "kW";
        s.psumZheng = gridZheng;
        s.psumFu = gridFu;
        s.psumStr = "kW";
        s.batteryPowerZheng = batZheng;
        s.batteryPowerFu = batFu;
        s.batteryPowerStr = "kW";
        return s;
    }

    private static StationDayEnergyResponse.Record totals(
            double pv, double buy, double sell, double load, double charge, double discharge) {
        StationDayEnergyResponse.Record r = new StationDayEnergyResponse.Record();
        r.energy = pv;
        r.energyStr = "kWh";
        r.gridPurchasedEnergy = buy;
        r.gridPurchasedEnergyStr = "kWh";
        r.gridSellEnergy = sell;
        r.gridSellEnergyStr = "kWh";
        r.homeLoadEnergy = load;
        r.homeLoadEnergyStr = "kWh";
        r.batteryChargeEnergy = charge;
        r.batteryChargeEnergyStr = "kWh";
        r.batteryDischargeEnergy = discharge;
        r.batteryDischargeEnergyStr = "kWh";
        r.date = JAN_DAY.toString();
        return r;
    }

    private static double sum(List<AlphaESSTransformedData> rows,
                              java.util.function.ToDoubleFunction<AlphaESSTransformedData> get) {
        return rows.stream().mapToDouble(get).sum();
    }

    // ── day shape ───────────────────────────────────────────────────────────

    @Test
    public void fullDayHas288SlotsAndMissingSlotsAreZero() {
        // Two samples only — the rest of the day must be zero-filled.
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(JAN_DAY, 12, 1, 31), 4.0, 1.0, 0, 3.0, 0, 0));
        samples.add(sample(millisAt(JAN_DAY, 12, 7, 2), 2.0, 1.0, 0, 1.0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(6.0, 0.0, 4.0, 2.0, 0, 0));

        assertEquals(288, rows.size());
        assertEquals("2026-01-15", rows.get(0).getDate());
        assertEquals("00:00", rows.get(0).getMinute());
        assertEquals("23:55", rows.get(287).getMinute());
        assertEquals(SYS_SN, rows.get(0).getSysSn());
        // Slot keys are exact 5-minute UTC millis.
        long dayStart = millisAt(JAN_DAY, 0, 0, 0);
        assertEquals(Long.valueOf(dayStart), rows.get(0).getMillisSinceEpoch());
        assertEquals(Long.valueOf(dayStart + 5 * 60_000L), rows.get(1).getMillisSinceEpoch());
        // 12:01:31 snapped into the 12:00 slot, 12:07:02 into 12:05.
        assertTrue(rows.get(144).getPv() > 0); // 12:00
        assertTrue(rows.get(145).getPv() > 0); // 12:05
        assertEquals(0.0, rows.get(0).getPv(), DELTA);
    }

    @Test
    public void samplesSharingASlotAreAveraged() {
        // Both samples in the 12:00 slot: pv 4 and 2 → shape average 3, and
        // the whole day's PV (one live slot) is normalised to the 6 kWh total.
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(JAN_DAY, 12, 0, 10), 4.0, 0, 0, 0, 0, 0));
        samples.add(sample(millisAt(JAN_DAY, 12, 3, 40), 2.0, 0, 0, 0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(6.0, 0, 0, 0, 0, 0));

        assertEquals(6.0, rows.get(144).getPv(), DELTA);
        assertEquals(6.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
    }

    // ── normalisation ───────────────────────────────────────────────────────

    @Test
    public void everyCurveSumsToItsDailyTotal() {
        List<StationDayResponse> samples = new ArrayList<>();
        // A morning of import + charge, an afternoon of export + discharge.
        samples.add(sample(millisAt(JAN_DAY, 8, 0, 0), 0.5, 1.0, 2.0, 0.0, 1.5, 0.0));
        samples.add(sample(millisAt(JAN_DAY, 13, 0, 0), 3.0, 1.2, 0.0, 1.8, 0.0, 0.7));
        samples.add(sample(millisAt(JAN_DAY, 14, 0, 0), 2.5, 0.8, 0.0, 1.4, 0.0, 0.5));

        StationDayEnergyResponse.Record totals =
                totals(12.0, 3.5, 5.0, 9.0, 2.0, 1.5);
        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals);

        assertEquals(12.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        assertEquals(3.5, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
        assertEquals(5.0, sum(rows, AlphaESSTransformedData::getFeed), DELTA);
        assertEquals(9.0, sum(rows, AlphaESSTransformedData::getLoad), DELTA);
        // charge is signed: net = charge − discharge.
        assertEquals(2.0 - 1.5, sum(rows, AlphaESSTransformedData::getCharge), DELTA);
    }

    @Test
    public void zeroTotalCurvesStayZero() {
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(JAN_DAY, 12, 0, 0), 3.0, 1.0, 0.5, 0, 0, 0));

        // PV total is zero even though the curve has values — trust the total.
        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(0.0, 1.0, 0.0, 2.0, 0, 0));

        assertEquals(0.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        assertEquals(1.0, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
    }

    // ── sign handling ───────────────────────────────────────────────────────

    @Test
    public void signedPsumSplitsWhenZhengFuAbsent() {
        List<StationDayResponse> samples = new ArrayList<>();
        StationDayResponse morning = sample(millisAt(JAN_DAY, 8, 0, 0), 0, 0, 0, 0, 0, 0);
        morning.psumZheng = null;
        morning.psumFu = null;
        morning.psum = 2.0; // one direction…
        StationDayResponse noon = sample(millisAt(JAN_DAY, 13, 0, 0), 0, 0, 0, 0, 0, 0);
        noon.psumZheng = null;
        noon.psumFu = null;
        noon.psum = -1.0; // …and the other
        samples.add(morning);
        samples.add(noon);

        // buy 4 kWh > sell 1 kWh, and the positive-psum curve is the larger
        // one, so positive psum pairs with buy.
        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(0, 4.0, 1.0, 0, 0, 0));

        assertEquals(4.0, sum(rows, AlphaESSTransformedData::getBuy), DELTA);
        assertEquals(1.0, sum(rows, AlphaESSTransformedData::getFeed), DELTA);
        // The 08:00 slot carries buy, the 13:00 slot carries feed.
        assertEquals(4.0, rows.get(96).getBuy(), DELTA);
        assertEquals(0.0, rows.get(96).getFeed(), DELTA);
        assertEquals(1.0, rows.get(156).getFeed(), DELTA);
    }

    @Test
    public void gridPairingFollowsTheLargerTotal() {
        // Same curves, flipped totals: now the larger (Zheng) curve must land
        // on FEED because sell > buy.
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(JAN_DAY, 8, 0, 0), 0, 0, 2.0, 0.0, 0, 0));
        samples.add(sample(millisAt(JAN_DAY, 13, 0, 0), 0, 0, 0.0, 1.0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(0, 1.0, 4.0, 0, 0, 0));

        assertEquals(4.0, rows.get(96).getFeed(), DELTA);  // 08:00 = Zheng slot
        assertEquals(1.0, rows.get(156).getBuy(), DELTA);  // 13:00 = Fu slot
    }

    @Test
    public void pairWithLargerTieKeepsFirstAssignment() {
        assertTrue(SolisDataMassager.pairWithLarger(0, 0, 0.0, 0.0));
        assertTrue(SolisDataMassager.pairWithLarger(2, 1, 4.0, 1.0));
        assertFalse(SolisDataMassager.pairWithLarger(2, 1, 1.0, 4.0));
    }

    // ── load fallback ───────────────────────────────────────────────────────

    @Test
    public void missingLoadCurveFallsBackToEnergyBalance() {
        // Grid-type plant: no familyLoadPower, no homeLoadEnergy.
        List<StationDayResponse> samples = new ArrayList<>();
        StationDayResponse s = sample(millisAt(JAN_DAY, 12, 0, 0), 3.0, 0, 1.0, 2.0, 0, 0);
        s.familyLoadPower = null;
        samples.add(s);

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(3.0, 1.0, 2.0, 0.0, 0, 0));

        // load = max(0, pv − feed + buy) per slot = 3 − 2 + 1 = 2 at noon.
        assertEquals(2.0, rows.get(144).getLoad(), DELTA);
        assertEquals(0.0, rows.get(0).getLoad(), DELTA);
    }

    // ── units ───────────────────────────────────────────────────────────────

    @Test
    public void unitTables() {
        assertEquals(1.5, SolisDataMassager.powerKw(1.5, "kW"), DELTA);
        assertEquals(0.0015, SolisDataMassager.powerKw(1.5, "W"), DELTA);
        assertEquals(1500.0, SolisDataMassager.powerKw(1.5, "MW"), DELTA);
        assertEquals(1.5, SolisDataMassager.powerKw(1.5, null), DELTA);
        assertNull(SolisDataMassager.powerKw(1.5, "hp"));
        assertEquals(0.0, SolisDataMassager.powerKw(null, "kW"), DELTA);

        assertEquals(2.5, SolisDataMassager.energyKwh(2.5, "kWh"), DELTA);
        assertEquals(0.0025, SolisDataMassager.energyKwh(2.5, "Wh"), DELTA);
        assertEquals(2500.0, SolisDataMassager.energyKwh(2.5, "MWh"), DELTA);
        assertNull(SolisDataMassager.energyKwh(2.5, "BTU"));
        assertNull(SolisDataMassager.energyKwh(null, "kWh"));
    }

    @Test
    public void unknownUnitSkipsTheSampleNotTheDay() {
        List<StationDayResponse> samples = new ArrayList<>();
        StationDayResponse bad = sample(millisAt(JAN_DAY, 11, 0, 0), 9.0, 0, 0, 0, 0, 0);
        bad.powerStr = "horsepower";
        samples.add(bad);
        samples.add(sample(millisAt(JAN_DAY, 12, 0, 0), 3.0, 0, 0, 0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(6.0, 0, 0, 0, 0, 0));

        assertEquals(288, rows.size());
        assertEquals(0.0, rows.get(132).getPv(), DELTA); // 11:00 skipped
        assertEquals(6.0, rows.get(144).getPv(), DELTA); // 12:00 carries it all
    }

    @Test
    public void mixedUnitsAreScaledBeforeNormalising() {
        // Same physical power expressed in W and kW must contribute equally.
        List<StationDayResponse> samples = new ArrayList<>();
        StationDayResponse watts = sample(millisAt(JAN_DAY, 11, 0, 0), 2000.0, 0, 0, 0, 0, 0);
        watts.powerStr = "W";
        samples.add(watts);
        samples.add(sample(millisAt(JAN_DAY, 12, 0, 0), 2.0, 0, 0, 0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, JAN_DAY, DUBLIN, samples, totals(6.0, 0, 0, 0, 0, 0));

        assertEquals(3.0, rows.get(132).getPv(), DELTA);
        assertEquals(3.0, rows.get(144).getPv(), DELTA);
    }

    // ── DST days (Europe/Dublin) ────────────────────────────────────────────

    @Test
    public void springForwardDayHas276Slots() {
        LocalDate springForward = LocalDate.of(2026, 3, 29); // 23-hour day
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(springForward, 12, 0, 0), 2.0, 0, 0, 0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, springForward, DUBLIN, samples, totals(4.0, 0, 0, 0, 0, 0));

        assertEquals(276, rows.size());
        assertEquals(4.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        assertEquals("00:55", rows.get(11).getMinute());
        assertEquals("02:00", rows.get(12).getMinute()); // 01:00–01:55 never exists
    }

    @Test
    public void fallBackDayHas300Slots() {
        LocalDate fallBack = LocalDate.of(2026, 10, 25); // 25-hour day
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(fallBack, 12, 0, 0), 2.0, 0, 0, 0, 0, 0));

        List<AlphaESSTransformedData> rows = SolisDataMassager.massage(
                SYS_SN, fallBack, DUBLIN, samples, totals(4.0, 0, 0, 0, 0, 0));

        assertEquals(300, rows.size());
        assertEquals(4.0, sum(rows, AlphaESSTransformedData::getPv), DELTA);
        // Every slot key is distinct UTC millis even where wall clocks repeat.
        long distinct = rows.stream()
                .map(AlphaESSTransformedData::getMillisSinceEpoch).distinct().count();
        assertEquals(300, distinct);
    }

    // ── degenerate input ────────────────────────────────────────────────────

    @Test
    public void missingSamplesOrTotalsYieldNoRows() {
        assertTrue(SolisDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN,
                new ArrayList<>(), totals(1, 1, 1, 1, 1, 1)).isEmpty());
        List<StationDayResponse> samples = new ArrayList<>();
        samples.add(sample(millisAt(JAN_DAY, 12, 0, 0), 1.0, 0, 0, 0, 0, 0));
        assertTrue(SolisDataMassager.massage(SYS_SN, JAN_DAY, DUBLIN,
                samples, null).isEmpty());
    }
}
