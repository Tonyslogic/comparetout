/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.esbn.responses;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class FetchRangeResponse {
    @SerializedName("minimumDate")
    public String minimumDate;
    @SerializedName("maximumDate")
    public String maximumDate;
    @SerializedName("onlyExport")
    public boolean onlyExport;
    @SerializedName("exportAndImport")
    public boolean exportAndImport;
    @SerializedName("imports")
    public List<ImportItem> imports;
    @SerializedName("exports")
    public List<ExportItem> exports;

    public static class ImportItem {
        // DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss") or DateTimeFormatter.ISO_OFFSET_DATE_TIME
        // 2024-01-01T00:00:00
        // 2024-01-23T03:00:00+00:00
        // Need to remove the '+00:00', or the parsing will complain
        @SerializedName("x")
        public String x;
        // Measurement of the previous 1/2 hour
        @SerializedName("y")
        public double y;
        // DateTimeFormatter.ofPattern("dd-MMM-yy 'at' HH:mm")
        @SerializedName("name")
        public String name;

    }

    public static class ExportItem {
        @SerializedName("x")
        public String x;
        @SerializedName("y")
        public double y;
        @SerializedName("name")
        public String name;

    }
}

