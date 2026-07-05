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

/**
 * {@code /v1/api/stationDay} — one element of the {@code data} array: a
 * power sample at ~5-minute cadence (but irregular stamps, e.g. 05:01:31).
 *
 * Sign conventions are NOT documented by the spec. Where present, the
 * "Zheng"/"Fu" (正/负 — positive/negative) split fields carry the two
 * directions of the grid and battery flows as separate non-negative values;
 * SolisDataMassager pairs each direction with the matching daily energy
 * total rather than assuming an orientation.
 */
public class StationDayResponse {
    /** Sample instant, epoch millis. */
    public Long time;
    public String timeStr;

    /** PV generation power. */
    public Double power;
    public String powerStr;

    /** Household load power. */
    public Double familyLoadPower;
    public String familyLoadPowerStr;

    /** Backup/bypass load power (EPS port). */
    public Double bypassLoadPower;
    public String bypassLoadPowerStr;

    /** Battery power, signed; Zheng/Fu carry the direction split when populated. */
    public Double batteryPower;
    public String batteryPowerStr;
    public Double batteryPowerZheng;
    public Double batteryPowerFu;

    /** Grid power, signed; Zheng/Fu carry the direction split when populated. */
    public Double psum;
    public String psumStr;
    public Double psumZheng;
    public Double psumFu;

    /** The station's UTC offset in hours for this sample's date. */
    public Double timeZone;
}
