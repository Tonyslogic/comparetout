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

package com.tfcode.comparetout.scenario;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;

/**
 * Fluent builder for {@link Battery} test fixtures.
 *
 * <p>Phase 0 test-support (see {@code plans/sim/refactor.md}). See {@link InverterBuilder} for the
 * rationale on package placement and visibility.</p>
 */
public class BatteryBuilder {

    private final Battery battery = new Battery();

    public static BatteryBuilder aBattery() {
        return new BatteryBuilder();
    }

    public BatteryBuilder index(long index) {
        battery.setBatteryIndex(index);
        return this;
    }

    public BatteryBuilder size(double kwh) {
        battery.setBatterySize(kwh);
        return this;
    }

    /** Discharge floor as a percentage of capacity (0-100). */
    public BatteryBuilder dischargeStopPercent(double percent) {
        battery.setDischargeStop(percent);
        return this;
    }

    /** Per-interval max charge / discharge (kWh per 5-minute step, matching the model). */
    public BatteryBuilder maxChargeDischarge(double maxCharge, double maxDischarge) {
        battery.setMaxCharge(maxCharge);
        battery.setMaxDischarge(maxDischarge);
        return this;
    }

    public BatteryBuilder storageLossPercent(double percent) {
        battery.setStorageLoss(percent);
        return this;
    }

    /** Name of the inverter this battery is connected to. */
    public BatteryBuilder inverter(String inverterName) {
        battery.setInverter(inverterName);
        return this;
    }

    public BatteryBuilder chargeModel(ChargeModel chargeModel) {
        battery.setChargeModel(chargeModel);
        return this;
    }

    public Battery build() {
        return battery;
    }
}
