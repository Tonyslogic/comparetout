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
 * A component that consumes surplus PV (the post-energy-flow {@code feed}) — e.g. hot-water divert and
 * EV divert. See {@code plans/sim/component.md}.
 *
 * <p><b>Order is a strategy, not a static priority.</b> Surplus is offered to the sinks in an order the
 * engine resolves per interval ({@link ComponentRegistry#divertOrder}) — water-first vs EV-first, driven
 * by {@code EVDivert.isEv1st()}. This is the exact analogue of how {@link DispatchStrategy} orders the
 * battery vs the grid for residual <i>load</i>: a config flag selects the order, and the engine makes a
 * single ordered pass. Each sink {@link #absorb}s once, so the legacy double-heat bug (water absorbed
 * twice) cannot occur; residual after a higher-priority sink simply flows to the next.</p>
 */
public interface SurplusSink extends SimComponent {

    /**
     * Consume up to {@code availableKWh} of surplus feed for this interval; return the kWh actually
     * absorbed. Implementations may consult and update cross-interval state they own (water
     * temperature, EV daily cap).
     */
    double absorb(double availableKWh, IntervalContext ctx);

    /** The output column this sink's absorbed energy is reported in (e.g. divToWater / divToEV). */
    OutputChannel divertChannel();
}
