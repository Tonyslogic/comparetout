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
 * {@code /v1/api/stationDayEnergyList} — the {@code data} element: daily
 * energy totals for every station under the account for one date. Unlike
 * stationDetail's "today" fields this works for historical dates, and one
 * call covers ALL stations, so the worker caches it per date.
 */
public class StationDayEnergyResponse {
    public Page page;

    public static class Page {
        public List<Record> records;
        public Integer current;
        public Integer pages;
        public Long total;
    }

    public static class Record {
        /** Station id — String, see StationListResponse. */
        public String id;
        public String stationName;
        /** PV generation for the day. */
        public Double energy;
        public String energyStr;
        public Double gridPurchasedEnergy;
        public String gridPurchasedEnergyStr;
        public Double gridSellEnergy;
        public String gridSellEnergyStr;
        public Double homeLoadEnergy;
        public String homeLoadEnergyStr;
        public Double batteryChargeEnergy;
        public String batteryChargeEnergyStr;
        public Double batteryDischargeEnergy;
        public String batteryDischargeEnergyStr;
        /** The day, yyyy-MM-dd. */
        public String date;
        public Double timeZone;
    }
}
