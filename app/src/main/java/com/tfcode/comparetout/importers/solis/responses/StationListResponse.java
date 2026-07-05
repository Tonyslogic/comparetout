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

package com.tfcode.comparetout.importers.solis.responses;

import java.util.List;

/**
 * {@code /v1/api/userStationList} — the {@code data} element. Only the
 * fields we consume are declared; Gson ignores the rest.
 *
 * Station ids are 19-digit numbers that exceed double precision, so every
 * id is declared as String (the API accepts string ids in request bodies).
 */
public class StationListResponse {
    public Page page;

    public static class Page {
        public List<Station> records;
        public Integer current;
        public Integer pages;
        public Long total;
    }

    public static class Station {
        public String id;
        public String stationName;
        /** Installed PV capacity, kWp. */
        public Double capacity;
        /** Plant type: 0 = grid-tied (no meter), 1 = storage, 3/4/5 = meter variants. */
        public Integer type;
        /** The station's own UTC offset in hours (may be fractional, e.g. 5.5). */
        public Double timeZone;
        public String timeZoneName;
        /** Currency code the plant reports in, e.g. "EUR" — echoed into stationDay requests. */
        public String money;
    }
}
