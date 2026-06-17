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

import com.tfcode.comparetout.model.scenario.Inverter;

/**
 * Fluent builder for {@link Inverter} test fixtures.
 *
 * <p>Phase 0 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). This is pure
 * test-support code: it builds the existing public model object and adds no production behaviour.
 * It lives in package {@code com.tfcode.comparetout.scenario} (alongside the existing simulation
 * tests) so that fixtures can be assembled next to the package-private {@code SimulationEngine.InputData},
 * and it is {@code public} so the future {@code scenario.sim} engine tests can reuse it.</p>
 */
public class InverterBuilder {

    private final Inverter inverter = new Inverter();

    public static InverterBuilder anInverter() {
        return new InverterBuilder();
    }

    public InverterBuilder index(long index) {
        inverter.setInverterIndex(index);
        return this;
    }

    public InverterBuilder name(String name) {
        inverter.setInverterName(name);
        return this;
    }

    public InverterBuilder maxInverterLoad(double kw) {
        inverter.setMaxInverterLoad(kw);
        return this;
    }

    public InverterBuilder minExcess(double kwh) {
        inverter.setMinExcess(kwh);
        return this;
    }

    public InverterBuilder mpptCount(int count) {
        inverter.setMpptCount(count);
        return this;
    }

    /** Percentage losses (0-100), matching the model's int representation. */
    public InverterBuilder losses(int dc2acLoss, int ac2dcLoss, int dc2dcLoss) {
        inverter.setDc2acLoss(dc2acLoss);
        inverter.setAc2dcLoss(ac2dcLoss);
        inverter.setDc2dcLoss(dc2dcLoss);
        return this;
    }

    /** A loss-free inverter, useful for isolating non-conversion behaviour in tests. */
    public InverterBuilder lossless() {
        return losses(0, 0, 0);
    }

    public Inverter build() {
        return inverter;
    }
}
