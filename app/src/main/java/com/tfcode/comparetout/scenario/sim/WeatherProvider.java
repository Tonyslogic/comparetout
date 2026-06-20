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

package com.tfcode.comparetout.scenario.sim;

/**
 * Outdoor weather aligned to the simulation grid (Phase 3 of {@code plans/hp/plan.md}). Given a canonical
 * UTC instant, returns the outdoor air temperature and wind speed the heat-pump model needs at that instant.
 *
 * <p>The source series is hourly (ERA5 / the synthetic fixture); a provider <b>interpolates</b> it onto the
 * sim grid — temperature and wind are smooth fields, so linear interpolation between the bracketing hours is
 * more faithful than the exact-millis snap PV uses (and, crucially, never zero-fills the gaps). Instants
 * outside the series clamp to the nearest endpoint.</p>
 *
 * <p>One implementation today: {@link CsvWeatherProvider}, which parses the raw ERA5 time-series CSV (the
 * fixture in tests / the shipped sample asset; the live CDS download in Phase 6 produces the identical CSV).</p>
 */
public interface WeatherProvider {

    /** Outdoor air temperature (°C) at the given UTC instant, interpolated onto the grid. */
    double temperatureAt(long millis);

    /** Wind speed (m/s) at the given UTC instant, interpolated onto the grid. */
    double windSpeedAt(long millis);
}
