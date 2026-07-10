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

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Parser for one SEMOpx {@code MarketResult_SEM-DA_*.csv} day-ahead result file.
 * <p>
 * Layout (verified against live files, 2026-07): semicolon-separated sections,
 * decimal-comma numbers, UTC ISO-8601 delivery-period timestamps. Two dialects
 * exist — early files (hourly era) use {@code Area set;SEM-DA} / {@code Market
 * Area;NI-DA} headers, current files (30-minute MTU) use {@code Auction;SEM-DA}
 * / {@code Market;NI-DA} — both share the section body:
 * <pre>
 *   Market;ROI-DA                  (or "Market Area;ROI-DA")
 *   Index prices;30;EUR            (resolution minutes; currency)
 *   2026-07-08T22:00:00Z;…         (period-start timestamps)
 *   275,94;240,00;…                (EUR/MWh, decimal comma)
 * </pre>
 * The single SEM clearing price is published under both NI-DA and ROI-DA with
 * identical EUR values; ROI-DA/EUR is parsed (NI-DA fallback). DST-length days
 * (46/50 half-hours, 23/25 hours) fall out naturally from the timestamp row.
 */
public final class SemopxDayResultCsv {

    private SemopxDayResultCsv() {}

    /** The EUR price series of one auction day: parallel period-start/price arrays. */
    public static final class DayResult {
        public final int resolutionMinutes;
        public final long[] utcMillis;
        public final double[] eurPerMwh;

        DayResult(int resolutionMinutes, long[] utcMillis, double[] eurPerMwh) {
            this.resolutionMinutes = resolutionMinutes;
            this.utcMillis = utcMillis;
            this.eurPerMwh = eurPerMwh;
        }
    }

    /**
     * Parse the ROI-DA (fallback NI-DA) EUR index-price section.
     *
     * @throws IOException when the file has no recognisable price section —
     *         the format changed or the download was truncated.
     */
    public static DayResult parse(String csv) throws IOException {
        String[] lines = csv.split("\r?\n");
        int section = findMarketSection(lines, "ROI-DA");
        if (section < 0) section = findMarketSection(lines, "NI-DA");
        if (section < 0) throw new IOException("No ROI-DA/NI-DA market section in SEM-DA csv");

        for (int i = section + 1; i < lines.length; i++) {
            String line = lines[i];
            // Stop at the next market section without finding prices.
            if (isMarketLine(line)) break;
            if (!line.startsWith("Index prices;")) continue;
            String[] header = line.split(";");
            if (header.length < 3 || !"EUR".equalsIgnoreCase(header[2].trim())) continue;
            if (i + 2 >= lines.length) break;
            int resolution;
            try {
                resolution = Integer.parseInt(header[1].trim());
            } catch (NumberFormatException nfe) {
                throw new IOException("Unparseable resolution in: " + line);
            }
            return parseSeries(resolution, lines[i + 1], lines[i + 2]);
        }
        throw new IOException("No EUR index-price section in SEM-DA csv");
    }

    private static boolean isMarketLine(String line) {
        return line.startsWith("Market;") || line.startsWith("Market Area;");
    }

    private static int findMarketSection(String[] lines, String area) {
        for (int i = 0; i < lines.length; i++) {
            if (isMarketLine(lines[i]) && lines[i].trim().endsWith(";" + area)) return i;
        }
        return -1;
    }

    private static DayResult parseSeries(int resolution, String stampLine, String priceLine)
            throws IOException {
        String[] stamps = stampLine.split(";");
        String[] prices = priceLine.split(";");
        if (stamps.length != prices.length || stamps.length == 0)
            throw new IOException("Timestamp/price rows disagree ("
                    + stamps.length + " vs " + prices.length + ")");
        long[] millis = new long[stamps.length];
        double[] eur = new double[stamps.length];
        for (int i = 0; i < stamps.length; i++) {
            try {
                millis[i] = Instant.parse(stamps[i].trim()).toEpochMilli();
            } catch (DateTimeParseException e) {
                throw new IOException("Bad period timestamp: " + stamps[i]);
            }
            try {
                eur[i] = Double.parseDouble(prices[i].trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IOException("Bad price: " + prices[i]);
            }
        }
        return new DayResult(resolution, millis, eur);
    }
}
