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

package com.tfcode.comparetout.importers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.model.ToutcDB;
import com.tfcode.comparetout.model.importers.CostInputRow;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.testdata.EnergyDataGenerator;
import com.tfcode.comparetout.util.RateLookup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * End-to-end guard for the shared-table path an importer feeds:
 * generated 5-minute data → {@code addTransformedData} (Room) → the
 * Compare/Costing read surface. Runs on the JVM via Robolectric (no device,
 * no FTL), so it rides the existing {@code testIeDebugUnitTest} CI gate.
 *
 * <p>The generator ({@link EnergyDataGenerator}) is the oracle: the DB
 * aggregation/cost results are checked back against the totals of the exact
 * rows that were inserted. The tariff is flat, so the buy cost has a
 * closed form (Σbuy × rate), isolating the SQL grouping/date handling in
 * {@code getSelectedAlphaESSData} — the seam {@code UI2CompareViewModel.sourceCosts}
 * uses.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ImportToCostingE2ETest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final LocalDate FROM = LocalDate.of(2001, 6, 18);
    private static final LocalDate TO = LocalDate.of(2001, 6, 24);   // 7 summer days
    private static final double DELTA = 1e-6;
    private static final DateTimeFormatter ROW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ToutcDB db;

    @Before
    public void setUp() {
        db = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), ToutcDB.class)
                .allowMainThreadQueries().build();
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void aggregationRoundTripsThroughTheDb() {
        String sysSn = "Solis-E2E";
        List<AlphaESSTransformedData> rows =
                EnergyDataGenerator.generate(sysSn, FROM, TO, DUBLIN);
        db.alphaEssDAO().addTransformedData(rows);

        double expectedPv = rows.stream().mapToDouble(AlphaESSTransformedData::getPv).sum();
        double expectedLoad = rows.stream().mapToDouble(AlphaESSTransformedData::getLoad).sum();
        double expectedBuy = rows.stream().mapToDouble(AlphaESSTransformedData::getBuy).sum();
        double expectedFeed = rows.stream().mapToDouble(AlphaESSTransformedData::getFeed).sum();
        assertTrue("generator should produce a non-trivial day", expectedPv > 0 && expectedBuy > 0);

        // sumHour buckets to 24 rows; summing them must reproduce the row totals.
        List<IntervalRow> hourly = db.alphaEssDAO().sumHour(sysSn, FROM.toString(), TO.toString());
        assertEquals(24, hourly.size());
        assertEquals(expectedPv, hourly.stream().mapToDouble(r -> r.pv).sum(), DELTA);
        assertEquals(expectedLoad, hourly.stream().mapToDouble(r -> r.load).sum(), DELTA);
        assertEquals(expectedBuy, hourly.stream().mapToDouble(r -> r.buy).sum(), DELTA);
        assertEquals(expectedFeed, hourly.stream().mapToDouble(r -> r.feed).sum(), DELTA);
    }

    @Test
    public void costingMatchesFlatTariffClosedForm() {
        String sysSn = "Solis-COST";
        List<AlphaESSTransformedData> rows =
                EnergyDataGenerator.generate(sysSn, FROM, TO, DUBLIN);
        db.alphaEssDAO().addTransformedData(rows);
        double totalBuyKwh = rows.stream().mapToDouble(AlphaESSTransformedData::getBuy).sum();

        double rate = 30.0; // c/kWh, flat all day, all year
        RateLookup lookup = new RateLookup(new PricePlan(), Collections.singletonList(
                flatRate("01/01", "12/31", rate)));
        lookup.setStartDOY(FROM.getDayOfYear());

        // Mirror UI2CompareViewModel.sourceCosts over the production query.
        List<CostInputRow> hourly = db.alphaEssDAO()
                .getSelectedAlphaESSData(sysSn, FROM.toString(), TO.toString());
        double buyCents = 0.0;
        double queriedBuyKwh = 0.0;
        for (CostInputRow row : hourly) {
            LocalDateTime ldt = LocalDateTime.parse(row.dateTime, ROW_FMT);
            int dow = ldt.getDayOfWeek().getValue() == 7 ? 0 : ldt.getDayOfWeek().getValue();
            double price = lookup.getRate(ldt.getDayOfYear(), ldt.getHour() * 60 + ldt.getMinute(),
                    dow, row.buy);
            buyCents += price * row.buy;
            queriedBuyKwh += row.buy;
        }

        // Query grouping must preserve every kWh, and a flat tariff costs Σbuy × rate.
        assertEquals(totalBuyKwh, queriedBuyKwh, DELTA);
        assertEquals(totalBuyKwh * rate, buyCents, 1e-4);
    }

    @Test
    public void solisAndFusionSolarClassifyDistinctlyButAggregateIdentically() {
        // Same shaped data under two source namespaces: they classify to
        // different importers but flow through the same shared-table query.
        List<AlphaESSTransformedData> solis =
                EnergyDataGenerator.generate("Solis-X", FROM, TO, DUBLIN);
        List<AlphaESSTransformedData> fusion =
                EnergyDataGenerator.generate("FusionSolar-Y", FROM, TO, DUBLIN);
        db.alphaEssDAO().addTransformedData(solis);
        db.alphaEssDAO().addTransformedData(fusion);

        assertEquals(ComparisonUIViewModel.Importer.SOLIS,
                ComparisonUIViewModel.Importer.forSysSn("Solis-X"));
        assertEquals(ComparisonUIViewModel.Importer.FUSION_SOLAR,
                ComparisonUIViewModel.Importer.forSysSn("FusionSolar-Y"));

        List<IntervalRow> a = db.alphaEssDAO().sumHour("Solis-X", FROM.toString(), TO.toString());
        List<IntervalRow> b = db.alphaEssDAO().sumHour("FusionSolar-Y", FROM.toString(), TO.toString());
        assertEquals(a.size(), b.size());
        assertEquals(a.stream().mapToDouble(r -> r.pv).sum(),
                b.stream().mapToDouble(r -> r.pv).sum(), DELTA);
        assertEquals(a.stream().mapToDouble(r -> r.buy).sum(),
                b.stream().mapToDouble(r -> r.buy).sum(), DELTA);
    }

    /** Flat all-day rate over one date range — mirrors RateLookupTest's helper. */
    private static DayRate flatRate(String start, String end, double cost) {
        DayRate dr = new DayRate();
        dr.setStartDate(start);
        dr.setEndDate(end);
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 1440, cost);
        dr.setMinuteRateRange(mrr);
        return dr;
    }
}
