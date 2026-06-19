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

import com.tfcode.comparetout.model.scenario.EVCharge;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled EV charging as a {@link DemandContributor} (Phase A of the component-registration refactor,
 * see {@code plans/sim/component.md}). When an EV charge is scheduled for the interval, its draw
 * contributes to the load and is recorded in {@link OutputChannel#DIRECT_EV_CHARGE}; otherwise the
 * contribution is zero (still recorded, matching the legacy engine which always set the column).
 *
 * <p>Byte-identical note: the demand is {@code draw / 12d}, reproducing the legacy arithmetic exactly
 * (not {@code draw * ctx.intervalHours}).</p>
 *
 * <p>The schedule lookup {@link #scheduledChargeOrNull} is the single source of truth — {@code
 * ScenarioInputs.isEVCharging} delegates to it.</p>
 */
public final class EvChargeComponent implements DemandContributor {

    private final List<EVCharge> evCharges;

    public EvChargeComponent(List<EVCharge> evCharges) {
        this.evCharges = evCharges;
    }

    @Override
    public DemandResult demand(IntervalContext ctx) {
        EVCharge evCharge = scheduledChargeOrNull(evCharges, ctx.dayOfWeek, ctx.month, ctx.minuteOfDay);
        double kWh = (null == evCharge) ? 0d : evCharge.getDraw() / 12d;
        Map<OutputChannel, Double> outputs = new EnumMap<>(OutputChannel.class);
        outputs.put(OutputChannel.DIRECT_EV_CHARGE, kWh);
        return new DemandResult(kWh, outputs);
    }

    /**
     * The EV charge scheduled for the given time, or null. Day-of-week 7 (Sun) is normalised to 0 to
     * match the stored schedule convention.
     */
    public static EVCharge scheduledChargeOrNull(List<EVCharge> evCharges,
                                                 int dayOfWeek, int monthOfYear, int minuteOfDay) {
        if (dayOfWeek == 7) dayOfWeek = 0;
        if (!(null == evCharges)) for (EVCharge evCharge : evCharges) {
            if (evCharge.getMonths().months.contains(monthOfYear) &&
                    evCharge.getDays().ints.contains(dayOfWeek) &&
                    evCharge.getBegin() * 60 <= minuteOfDay &&
                    evCharge.getEnd() * 60 > minuteOfDay) {
                return evCharge;
            }
        }
        return null;
    }
}
