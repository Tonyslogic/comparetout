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
 * Stores and releases energy — the battery (Phase C of the component-registration refactor, see
 * {@code plans/sim/component.md}). State of charge (SOC) lives here; the engine reads it and applies the
 * deltas it computes in the bus solve (the physics stays in the engine — the component only holds state).
 */
public interface Storage extends SimComponent {

    /** Current state of charge (kWh, DC). */
    double soc();

    /** Apply a SOC change (kWh, DC). Positive charges, negative discharges. */
    void adjustSoc(double deltaKWh);

    /** Set the SOC directly (kWh, DC) — used for per-run initialisation. */
    void setSoc(double socKWh);

    /** Charge capacity available at the current SOC (kWh, DC), bounded by the charge model. */
    double getChargeCapacity();

    /** Discharge capacity available above the discharge stop for the given row (kWh, DC). */
    double getDischargeCapacity(int row);

    /** Discharge-stop floor (kWh, DC) — the reserve the battery will not discharge below. */
    double getDischargeStop();

    /** Storage (round-trip) loss as a percentage. */
    double storageLoss();

    /** Battery capacity (kWh). */
    double batterySize();
}
