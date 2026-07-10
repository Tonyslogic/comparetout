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

package com.tfcode.comparetout.dynamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;
import java.time.Instant;

/**
 * Fixtures are abbreviated copies of live SEMOpx files (verified 2026-07-10):
 * the current 30-minute dialect ("Auction;SEM-DA" / "Market;ROI-DA") and the
 * hourly-era dialect ("Area set;SEM-DA" / "Market Area;ROI-DA").
 */
public class SemopxDayResultCsvTest {

    private static final double DELTA = 1e-9;

    private static final String CURRENT_DIALECT = String.join("\n",
            "Auction;SEM-DA",
            "Auction name;PWR-MRC-D+1",
            "Auction date time;2026-07-08T10:00:00Z",
            "Publication date time;2026-07-08T10:55:00Z",
            "FX rates",
            "EUR;GBP;0,85455478",
            "Market;NI-DA",
            "Index prices;30;EUR",
            "2026-07-08T22:00:00Z;2026-07-08T22:30:00Z",
            "111,11;222,22",
            "Index prices;30;GBP",
            "2026-07-08T22:00:00Z;2026-07-08T22:30:00Z",
            "94,95;189,90",
            "Market;ROI-DA",
            "Index prices;30;EUR",
            "2026-07-08T22:00:00Z;2026-07-08T22:30:00Z",
            "275,94;240,00",
            "Index volumes;30",
            "2026-07-08T22:00:00Z;2026-07-08T22:30:00Z",
            "3726,1;3641,1");

    private static final String HOURLY_DIALECT = String.join("\n",
            "Area set;SEM-DA",
            "Auction name;PWR-MRC-D+1",
            "Auction date time;2019-01-15T11:00:00Z",
            "FX rates",
            "EUR;GBP;0,88865192",
            "Market Area;NI-DA",
            "Index prices;60;EUR",
            "2019-01-15T23:00:00Z;2019-01-16T00:00:00Z;2019-01-16T01:00:00Z",
            "56,850;54,163;53,607",
            "Market Area;ROI-DA",
            "Index prices;60;EUR",
            "2019-01-15T23:00:00Z;2019-01-16T00:00:00Z;2019-01-16T01:00:00Z",
            "56,850;54,163;53,607");

    @Test
    public void parsesCurrentThirtyMinuteDialect() throws IOException {
        SemopxDayResultCsv.DayResult day = SemopxDayResultCsv.parse(CURRENT_DIALECT);
        assertEquals(30, day.resolutionMinutes);
        assertEquals(2, day.utcMillis.length);
        assertEquals(Instant.parse("2026-07-08T22:00:00Z").toEpochMilli(), day.utcMillis[0]);
        // ROI-DA section, not NI-DA (111,11) and not GBP (94,95).
        assertEquals(275.94, day.eurPerMwh[0], DELTA);
        assertEquals(240.00, day.eurPerMwh[1], DELTA);
    }

    @Test
    public void parsesHourlyEraDialect() throws IOException {
        SemopxDayResultCsv.DayResult day = SemopxDayResultCsv.parse(HOURLY_DIALECT);
        assertEquals(60, day.resolutionMinutes);
        assertEquals(3, day.utcMillis.length);
        assertEquals(Instant.parse("2019-01-15T23:00:00Z").toEpochMilli(), day.utcMillis[0]);
        assertEquals(56.850, day.eurPerMwh[0], DELTA);
    }

    @Test
    public void fallsBackToNiDaWhenRoiDaAbsent() throws IOException {
        String niOnly = String.join("\n",
                "Auction;SEM-DA",
                "Market;NI-DA",
                "Index prices;30;EUR",
                "2026-07-08T22:00:00Z",
                "111,11");
        SemopxDayResultCsv.DayResult day = SemopxDayResultCsv.parse(niOnly);
        assertEquals(111.11, day.eurPerMwh[0], DELTA);
    }

    @Test
    public void rejectsFilesWithoutAPriceSection() {
        assertThrows(IOException.class, () -> SemopxDayResultCsv.parse("Auction;SEM-DA\nFX rates"));
        assertThrows(IOException.class, () -> SemopxDayResultCsv.parse(
                "Market;ROI-DA\nIndex volumes;30\n2026-07-08T22:00:00Z\n1,0"));
    }

    @Test
    public void rejectsMismatchedRows() {
        String mismatched = String.join("\n",
                "Market;ROI-DA",
                "Index prices;30;EUR",
                "2026-07-08T22:00:00Z;2026-07-08T22:30:00Z",
                "275,94");
        assertThrows(IOException.class, () -> SemopxDayResultCsv.parse(mismatched));
    }
}
