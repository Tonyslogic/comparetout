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

package com.tfcode.comparetout.importers.alphaess;

/**
 * Per-interval flow decomposition for AlphaESS processed data.
 *
 * Given the four energy-balanced quantities for a 5-minute interval — pv, load,
 * feed, buy — this class allocates them across the conceptual flow paths:
 * solar→load, solar→battery, solar→grid, battery→load, battery→grid,
 * grid→load, grid→battery. The allocation is deterministic and exact
 * (batChargeIn − batDischargeOut always equals pv + buy − load − feed).
 *
 * Allocation priority is: PV serves load first, then export, then battery;
 * grid serves load first, then battery; battery covers what is left. This
 * preserves energy balance even when a 5-minute average shows simultaneous
 * import and export (the system briefly oscillated within the window) — that
 * case is represented as non-zero values for BOTH batChargeIn and
 * batDischargeOut, whose net is still the correct signed delta.
 */
public final class AlphaESSFlowDecomposer {

    private AlphaESSFlowDecomposer() {}

    public static final class FlowDecomposition {
        public final double pv2load;
        public final double pv2bat;
        public final double pv2grid;
        public final double bat2load;
        public final double bat2grid;
        public final double grid2load;
        public final double grid2bat;
        public final double batChargeIn;
        public final double batDischargeOut;

        public FlowDecomposition(double pv2load, double pv2bat, double pv2grid,
                                 double bat2load, double bat2grid,
                                 double grid2load, double grid2bat,
                                 double batChargeIn, double batDischargeOut) {
            this.pv2load = pv2load;
            this.pv2bat = pv2bat;
            this.pv2grid = pv2grid;
            this.bat2load = bat2load;
            this.bat2grid = bat2grid;
            this.grid2load = grid2load;
            this.grid2bat = grid2bat;
            this.batChargeIn = batChargeIn;
            this.batDischargeOut = batDischargeOut;
        }

        public static final FlowDecomposition ZERO = new FlowDecomposition(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Allocate per-interval energy flows.
     *
     * Inputs are kWh, non-negative; negative values are clamped to zero. Sign of
     * the implicit charge (pv + buy − load − feed) determines whether the result
     * has non-zero batChargeIn (charging), batDischargeOut (discharging), or a
     * mix (transient oscillation).
     */
    public static FlowDecomposition decompose(double pv, double load, double feed, double buy) {
        if (pv < 0) pv = 0;
        if (load < 0) load = 0;
        if (feed < 0) feed = 0;
        if (buy < 0) buy = 0;

        double pv2load = Math.min(pv, load);
        double remainingPv = pv - pv2load;
        double pv2grid = Math.min(remainingPv, feed);
        remainingPv -= pv2grid;
        double pv2bat = remainingPv;
        double bat2grid = feed - pv2grid;
        double remainingLoad = load - pv2load;
        double grid2load = Math.min(remainingLoad, buy);
        double grid2bat = buy - grid2load;
        double bat2load = remainingLoad - grid2load;
        double batChargeIn = pv2bat + grid2bat;
        double batDischargeOut = bat2load + bat2grid;
        return new FlowDecomposition(pv2load, pv2bat, pv2grid,
                bat2load, bat2grid,
                grid2load, grid2bat,
                batChargeIn, batDischargeOut);
    }
}
