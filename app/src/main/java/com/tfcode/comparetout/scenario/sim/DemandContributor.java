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
 * A component that adds scheduled electrical demand to the load before the energy flow (so PV/battery
 * can try to cover it). Implemented today by EV scheduled charge (and, after Phase B, hot-water
 * immersion); a heat pump's electrical demand will be one more implementor. See
 * {@code plans/sim/component.md}.
 */
public interface DemandContributor extends SimComponent {

    /**
     * The electrical demand this component wants for the given interval, plus any output-row
     * contributions. The engine adds {@link DemandResult#kWh} to the load and routes
     * {@link DemandResult#outputs}. Implementations may consult cross-interval state they own.
     */
    DemandResult demand(IntervalContext ctx);
}
