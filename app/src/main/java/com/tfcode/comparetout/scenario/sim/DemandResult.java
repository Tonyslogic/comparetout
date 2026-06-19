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

import java.util.Map;

/**
 * What a {@link DemandContributor} wants this interval: the electrical {@link #kWh} the engine adds to
 * the load, plus any {@link #outputs} the engine routes onto the output row. See
 * {@code plans/sim/component.md}.
 */
public final class DemandResult {

    /** Electrical demand this interval (kWh), added to the load before the energy flow. */
    public final double kWh;
    /** Output-row contributions to route via {@link OutputChannel} (may be empty, never null). */
    public final Map<OutputChannel, Double> outputs;

    public DemandResult(double kWh, Map<OutputChannel, Double> outputs) {
        this.kWh = kWh;
        this.outputs = outputs;
    }
}
