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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Streamed reader of SEMOpx's {@code Lookback2_mkt.xlsx} look-back workbook —
 * the third (gap-closing) publication behind {@link SemopxRateSource}. The
 * workbook aggregates auction results the daily feeds no longer publish
 * (verified live 2026-07: DAM hourly, 2021-01 → the workbook's build month,
 * currently Sep-2024; SEMOpx refreshes it sporadically, so coverage grows).
 * <p>
 * An xlsx is a zip of SpreadsheetML parts. Only four are read, all streamed
 * through SAX (no POI, no DOM — the raw sheet alone is ~94&nbsp;MB of XML):
 * <ol>
 *   <li>{@code xl/workbook.xml} — resolve the sheet named {@value #SHEET_NAME}
 *       to its relationship id;</li>
 *   <li>{@code xl/_rels/workbook.xml.rels} — relationship id → sheet part;</li>
 *   <li>{@code xl/sharedStrings.xml} — the string table (timestamps and
 *       auction names are shared strings in the live build);</li>
 *   <li>the sheet part — rows of {@code auction | timestamp | price_eur |
 *       …} (columns A/B/C, verified against the live header row). Rows whose
 *       auction is not {@code DAM} (IDA1/2/3 intraday) are skipped.</li>
 * </ol>
 * Timestamps are UTC delivery-period starts, ISO-8601 in the live build with a
 * defensive fallback to Excel serial date numbers. Prices are EUR/MWh, the
 * same unit the daily CSVs carry into {@link SeriesNormaliser}.
 */
public final class SemopxLookbackXlsx {

    private SemopxLookbackXlsx() {}

    /** The raw DAM/IDA auction table's sheet name (verified live, 2026-07). */
    static final String SHEET_NAME = "auctions_to";

    private static final String DAM = "DAM";
    /** Days from the Excel 1900-system epoch (serial 0 = 1899-12-30) to Unix epoch. */
    private static final double EXCEL_UNIX_OFFSET_DAYS = 25569d;
    private static final double DAY_MILLIS = 86_400_000d;
    private static final long HALF_HOUR = 30L * 60_000L;
    private static final long HOUR = 60L * 60_000L;

    /**
     * Read the workbook's whole DAM price series: UTC period-start millis →
     * EUR/MWh. Non-DAM rows and unparseable rows are skipped; a workbook
     * without the {@value #SHEET_NAME} sheet (format change, truncated
     * download) is an {@link IOException} so the caller treats the cached
     * file as corrupt.
     */
    public static NavigableMap<Long, Double> readDamPrices(File xlsx) throws IOException {
        try (ZipFile zip = new ZipFile(xlsx)) {
            String relId = findSheetRelId(zip);
            if (null == relId)
                throw new IOException("No '" + SHEET_NAME + "' sheet in " + xlsx.getName());
            String sheetPath = resolveRelTarget(zip, relId);
            if (null == sheetPath || null == zip.getEntry(sheetPath))
                throw new IOException("Sheet part missing for '" + SHEET_NAME + "'");
            List<String> shared = readSharedStrings(zip);
            SheetHandler handler = new SheetHandler(shared);
            parsePart(zip, sheetPath, handler);
            return handler.prices;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Unreadable workbook: " + e.getMessage(), e);
        }
    }

    /**
     * Slice a (sub)map of the series into {@link SemopxDayResultCsv.DayResult}
     * runs of uniform resolution, ready for {@link SeriesNormaliser#assembleMonth}.
     * Runs break on coverage holes and on a 60↔30-minute resolution change (the
     * DAM switched to half-hourly periods after this workbook's hourly era); a
     * lone stamp defaults to hourly and the normaliser's gap handling absorbs
     * the imprecision.
     */
    static List<SemopxDayResultCsv.DayResult> toDayResults(NavigableMap<Long, Double> prices) {
        List<SemopxDayResultCsv.DayResult> out = new ArrayList<>();
        Long[] ts = prices.keySet().toArray(new Long[0]);
        int n = ts.length;
        int i = 0;
        while (i < n) {
            long res = -1;
            int j = i;
            while (j + 1 < n) {
                long gap = ts[j + 1] - ts[j];
                if (gap != HALF_HOUR && gap != HOUR) break;
                if (res == -1) res = gap;
                else if (gap != res) break;
                j++;
            }
            if (res == -1) res = HOUR;
            long[] millis = new long[j - i + 1];
            double[] eur = new double[j - i + 1];
            for (int k = i; k <= j; k++) {
                millis[k - i] = ts[k];
                eur[k - i] = prices.get(ts[k]);
            }
            out.add(new SemopxDayResultCsv.DayResult((int) (res / 60_000L), millis, eur));
            i = j + 1;
        }
        return out;
    }

    // ── SpreadsheetML plumbing ─────────────────────────────────────────────

    private static void parsePart(ZipFile zip, String path, DefaultHandler handler)
            throws IOException, ParserConfigurationException, SAXException {
        ZipEntry entry = zip.getEntry(path);
        if (null == entry) throw new IOException("Missing workbook part: " + path);
        try (InputStream in = new BufferedInputStream(zip.getInputStream(entry))) {
            newParser().parse(in, handler);
        }
    }

    private static SAXParser newParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        // XXE hardening; features vary by implementation (JDK vs Android Expat),
        // so each is best-effort.
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newSAXParser();
    }

    private static void trySetFeature(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Not supported by this parser — acceptable for trusted-origin files.
        }
    }

    /** workbook.xml: the r:id of the sheet named {@link #SHEET_NAME}, or null. */
    private static String findSheetRelId(ZipFile zip)
            throws IOException, ParserConfigurationException, SAXException {
        final String[] relId = {null};
        parsePart(zip, "xl/workbook.xml", new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                                     Attributes attributes) {
                if (!"sheet".equals(qName)) return;
                if (SHEET_NAME.equals(attributes.getValue("name")))
                    relId[0] = attributes.getValue("r:id");
            }
        });
        return relId[0];
    }

    /** workbook.xml.rels: relationship id → zip path of the target part. */
    private static String resolveRelTarget(ZipFile zip, String relId)
            throws IOException, ParserConfigurationException, SAXException {
        final Map<String, String> targets = new HashMap<>();
        parsePart(zip, "xl/_rels/workbook.xml.rels", new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                                     Attributes attributes) {
                if (!"Relationship".equals(qName)) return;
                targets.put(attributes.getValue("Id"), attributes.getValue("Target"));
            }
        });
        String target = targets.get(relId);
        if (null == target) return null;
        // Targets are workbook-relative ("worksheets/sheet5.xml"); absolute
        // ones ("/xl/…") are package-rooted.
        return target.startsWith("/") ? target.substring(1) : "xl/" + target;
    }

    /** sharedStrings.xml as a list — text runs under each {@code <si>} concatenated. */
    private static List<String> readSharedStrings(ZipFile zip)
            throws IOException, ParserConfigurationException, SAXException {
        final List<String> shared = new ArrayList<>();
        if (null == zip.getEntry("xl/sharedStrings.xml")) return shared;
        parsePart(zip, "xl/sharedStrings.xml", new DefaultHandler() {
            private final StringBuilder current = new StringBuilder();
            private boolean inText;

            @Override
            public void startElement(String uri, String localName, String qName,
                                     Attributes attributes) {
                if ("si".equals(qName)) current.setLength(0);
                else if ("t".equals(qName)) inText = true;
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (inText) current.append(ch, start, length);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if ("t".equals(qName)) inText = false;
                else if ("si".equals(qName)) shared.add(current.toString());
            }
        });
        return shared;
    }

    /** Streams the auction sheet, keeping (timestamp, price_eur) of DAM rows. */
    private static final class SheetHandler extends DefaultHandler {
        final NavigableMap<Long, Double> prices = new TreeMap<>();

        private final List<String> shared;
        private final StringBuilder value = new StringBuilder();
        private boolean capture;
        private String cellColumn;
        private String cellType;
        private int cellIndex; // fallback when a <c> carries no r attribute
        private String auction;
        private String timestamp;
        private String priceEur;

        SheetHandler(List<String> shared) {
            this.shared = shared;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attributes) {
            switch (qName) {
                case "row":
                    auction = null;
                    timestamp = null;
                    priceEur = null;
                    cellIndex = 0;
                    break;
                case "c":
                    cellColumn = columnOf(attributes.getValue("r"), cellIndex++);
                    cellType = attributes.getValue("t");
                    value.setLength(0);
                    break;
                case "v":
                case "t": // inlineStr runs (<is><t>) — not seen live, cheap to honour
                    capture = isWantedColumn(cellColumn);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (capture) value.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case "v":
                case "t":
                    capture = false;
                    break;
                case "c":
                    assignCell();
                    break;
                case "row":
                    finishRow();
                    break;
                default:
                    break;
            }
        }

        private void assignCell() {
            if (!isWantedColumn(cellColumn) || value.length() == 0) return;
            String resolved = resolve(cellType, value.toString());
            if (null == resolved) return;
            switch (cellColumn) {
                case "A": auction = resolved; break;
                case "B": timestamp = resolved; break;
                case "C": priceEur = resolved; break;
                default: break;
            }
        }

        private void finishRow() {
            // Header and IDA1/2/3 (intraday) rows fall out on the DAM filter.
            if (!DAM.equals(auction) || null == timestamp || null == priceEur) return;
            Long millis = parseTimestamp(timestamp);
            if (null == millis) return;
            try {
                prices.put(millis, Double.parseDouble(priceEur.trim()));
            } catch (NumberFormatException ignored) {
                // One malformed price row is not worth failing 33k good ones.
            }
        }

        private String resolve(String type, String raw) {
            if ("s".equals(type)) {
                try {
                    return shared.get(Integer.parseInt(raw.trim()));
                } catch (RuntimeException e) {
                    return null;
                }
            }
            return raw; // n (numeric), str (formula string), inlineStr text
        }

        private static boolean isWantedColumn(String column) {
            return "A".equals(column) || "B".equals(column) || "C".equals(column);
        }

        private static String columnOf(String cellRef, int fallbackIndex) {
            if (null == cellRef) {
                // No r attribute: cells are positional. Only A..Z matter here.
                return fallbackIndex < 26 ? String.valueOf((char) ('A' + fallbackIndex)) : "?";
            }
            int end = 0;
            while (end < cellRef.length() && Character.isLetter(cellRef.charAt(end))) end++;
            return cellRef.substring(0, end);
        }

        private static Long parseTimestamp(String raw) {
            String trimmed = raw.trim();
            try {
                return Instant.parse(trimmed).toEpochMilli();
            } catch (DateTimeParseException notIso) {
                try {
                    // Excel serial date-time (1900 system): days since 1899-12-30.
                    double serial = Double.parseDouble(trimmed);
                    return Math.round((serial - EXCEL_UNIX_OFFSET_DAYS) * DAY_MILLIS);
                } catch (NumberFormatException notSerial) {
                    return null;
                }
            }
        }
    }
}
