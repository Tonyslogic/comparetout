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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds minimal in-memory xlsx workbooks shaped like SEMOpx's
 * {@code Lookback2_mkt.xlsx} (an {@code auctions_to} sheet whose columns are
 * {@code auction | timestamp | price_eur}, auction names and timestamps in the
 * shared-string table — mirrors the live file's encoding, verified 2026-07).
 */
final class LookbackXlsxFixture {

    private LookbackXlsxFixture() {}

    /** One sheet row: auction name, ISO timestamp (or Excel serial), EUR/MWh. */
    static final class Row {
        final String auction;
        final String timestamp;
        final double priceEur;
        /** true = timestamp written as an inline numeric cell (Excel serial). */
        final boolean numericTimestamp;

        Row(String auction, String timestamp, double priceEur) {
            this(auction, timestamp, priceEur, false);
        }

        Row(String auction, String timestamp, double priceEur, boolean numericTimestamp) {
            this.auction = auction;
            this.timestamp = timestamp;
            this.priceEur = priceEur;
            this.numericTimestamp = numericTimestamp;
        }
    }

    /** Standard workbook: auctions_to → rId5 → worksheets/sheet5.xml. */
    static byte[] workbook(List<Row> rows) throws IOException {
        return workbook(rows, "auctions_to", "worksheets/sheet5.xml");
    }

    /** Workbook with a custom sheet name / part path (rels-resolution tests). */
    static byte[] workbook(List<Row> rows, String sheetName, String sheetTarget)
            throws IOException {
        // Shared strings: header cells + every auction name and string timestamp.
        Map<String, Integer> shared = new LinkedHashMap<>();
        String[] header = {"auction", "timestamp", "price_eur"};
        for (String h : header) intern(shared, h);
        for (Row row : rows) {
            intern(shared, row.auction);
            if (!row.numericTimestamp) intern(shared, row.timestamp);
        }

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetData>");
        sheet.append("<row r=\"1\">");
        for (int c = 0; c < header.length; c++) {
            sheet.append("<c r=\"").append((char) ('A' + c)).append("1\" t=\"s\"><v>")
                    .append(shared.get(header[c])).append("</v></c>");
        }
        sheet.append("</row>");
        int r = 2;
        for (Row row : rows) {
            sheet.append("<row r=\"").append(r).append("\">");
            sheet.append("<c r=\"A").append(r).append("\" t=\"s\"><v>")
                    .append(shared.get(row.auction)).append("</v></c>");
            if (row.numericTimestamp) {
                sheet.append("<c r=\"B").append(r).append("\"><v>")
                        .append(row.timestamp).append("</v></c>");
            } else {
                sheet.append("<c r=\"B").append(r).append("\" t=\"s\"><v>")
                        .append(shared.get(row.timestamp)).append("</v></c>");
            }
            sheet.append("<c r=\"C").append(r).append("\"><v>")
                    .append(row.priceEur).append("</v></c>");
            sheet.append("</row>");
            r++;
        }
        sheet.append("</sheetData></worksheet>");

        StringBuilder sst = new StringBuilder();
        sst.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String s : shared.keySet())
            sst.append("<si><t>").append(s).append("</t></si>");
        sst.append("</sst>");

        String workbookXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets>"
                + "<sheet name=\"Pivot\" sheetId=\"2\" r:id=\"rId1\"/>"
                + "<sheet name=\"" + sheetName + "\" sheetId=\"1\" r:id=\"rId5\"/>"
                + "</sheets></workbook>";
        String relsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/pivot.xml\"/>"
                + "<Relationship Id=\"rId5\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"" + sheetTarget + "\"/>"
                + "</Relationships>";

        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("xl/workbook.xml", workbookXml);
        parts.put("xl/_rels/workbook.xml.rels", relsXml);
        parts.put("xl/sharedStrings.xml", sst.toString());
        parts.put("xl/" + sheetTarget, sheet.toString());
        parts.put("xl/worksheets/pivot.xml",
                "<?xml version=\"1.0\"?><worksheet><sheetData/></worksheet>");
        return zip(parts);
    }

    /** Hourly DAM rows covering whole UTC days from {@code startIso}, plus a decoy IDA row. */
    static List<Row> hourlyDamDays(String startIso, int days, double priceEur) {
        List<Row> rows = new ArrayList<>();
        java.time.Instant start = java.time.Instant.parse(startIso);
        for (int h = 0; h < days * 24; h++) {
            rows.add(new Row("DAM", start.plusSeconds(h * 3600L).toString(), priceEur));
        }
        rows.add(new Row("IDA1", startIso, 9999.0));
        return rows;
    }

    private static void intern(Map<String, Integer> shared, String value) {
        shared.putIfAbsent(value, shared.size());
    }

    private static byte[] zip(Map<String, String> parts) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
