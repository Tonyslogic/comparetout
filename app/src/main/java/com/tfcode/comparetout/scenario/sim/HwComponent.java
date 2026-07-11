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

import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Hot water as a component (Phase B of the component-registration refactor, see
 * {@code plans/sim/component.md}). It is both a {@link DemandContributor} (scheduled immersion load,
 * pre-energy-flow) and a {@link SurplusSink} (divert surplus PV into the cylinder, post-energy-flow),
 * and it <b>owns the water temperature across intervals</b> — the engine no longer carries it via the
 * previous output row.
 *
 * <p>Byte-identical reproduction of the legacy engine:</p>
 * <ul>
 *   <li>All heating in an interval is computed from {@code previousWaterTemp} (the prior interval's
 *       final temperature), captured once in {@link #demand} and held in {@link #prevTemp}. Scheduled
 *       and divert heating update a transient {@link #nowTemp}; {@link #commitWaterTemp} promotes it to
 *       the carried {@link #waterTemp} at interval end.</li>
 *   <li>Scheduled immersion runs only when {@code (immersionIsOn || !hwDiversionIsOn)} and a system is
 *       present; divert runs only when {@code (!immersionIsOn && hwDiversionIsOn)} — the legacy guards.
 *       These flags are resolved in {@link #demand}, so the engine must call {@code demand} before
 *       {@link #absorb}/{@link #canDivert} each interval (it does: demand phase precedes the divert
 *       phase).</li>
 *   <li>Rates use {@code / 12d}, matching the legacy arithmetic exactly (not {@code * intervalHours}).</li>
 * </ul>
 *
 * <p><b>Double-heat fixed.</b> The engine now makes a single ordered divert pass
 * ({@link ComponentRegistry#divertOrder}), so {@link #absorb} is called at most once per interval. The
 * legacy bug — water heated twice from {@code previousWaterTemp}, double-debiting feed and discarding the
 * first heating — is gone. The {@code prevTemp} snapshot is retained because it is the correct basis for
 * the single heating (water heats from the interval's starting temperature).</p>
 */
public final class HwComponent implements DemandContributor, SurplusSink {

    private final HWSystem hwSystem;
    private final Boolean hwDivert;
    private final List<HWSchedule> hwSchedules;
    // Derive the UTC day-of-month only when some schedule actually carries a
    // v16 date window — the defaults path stays allocation-free per interval.
    private final boolean anyDateWindowed;

    /** Water temperature carried into the next interval (the previous interval's final value). */
    private double waterTemp = 0d;

    // Per-interval transients, resolved in demand() and consumed by absorb()/canDivert()/commit().
    private double prevTemp = 0d;
    private double nowTemp = 0d;
    private boolean immersionIsOn = false;
    private boolean hwDiversionIsOn = false;

    public HwComponent(HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hwSchedules) {
        this.hwSystem = hwSystem;
        this.hwDivert = hwDivert;
        this.hwSchedules = hwSchedules;
        boolean windowed = false;
        if (!(null == hwSchedules)) for (HWSchedule s : hwSchedules) {
            if (!ScheduleDateWindow.isDefault(s.getStartDate(), s.getEndDate())) windowed = true;
        }
        this.anyDateWindowed = windowed;
    }

    /**
     * Resets the carried temperature for a fresh run. The engine calls this on the first simulated
     * interval, reproducing the legacy "previous temperature is 0 when there is no prior output row"
     * — so a re-run (or a reused scenario) is deterministic regardless of leftover state.
     */
    public void resetWaterTemp() {
        waterTemp = 0d;
    }

    @Override
    public DemandResult demand(IntervalContext ctx) {
        prevTemp = waterTemp;
        nowTemp = prevTemp;
        int dayOfMonth = anyDateWindowed ? ScheduleDateWindow.dayOfMonthUtc(ctx.millis) : 0;
        immersionIsOn = isHotWaterHeatingScheduled(hwSchedules, ctx.dayOfWeek, ctx.month,
                dayOfMonth, ctx.minuteOfDay);
        hwDiversionIsOn = !(null == hwSystem) && !(null == hwDivert) && hwDivert;

        double draw = 0d;
        if (immersionIsOn && !(null == hwSystem)) draw = hwSystem.getHwRate() / 12d;
        double scheduledWaterLoad = 0d;
        if ((immersionIsOn || !hwDiversionIsOn) && !(null == hwSystem)) {
            HWSystem.Heat heat = hwSystem.heatWater(ctx.minuteOfDay, prevTemp, draw);
            scheduledWaterLoad = heat.kWhUsed;
            nowTemp = heat.temperature;
        }

        Map<OutputChannel, Double> outputs = new EnumMap<>(OutputChannel.class);
        outputs.put(OutputChannel.IMMERSION_LOAD, scheduledWaterLoad);
        return new DemandResult(scheduledWaterLoad, outputs);
    }

    /** True when divert heating applies this interval ({@code !immersionIsOn && hwDiversionIsOn}). */
    public boolean canDivert() {
        return !immersionIsOn && hwDiversionIsOn;
    }

    @Override
    public double absorb(double availableKWh, IntervalContext ctx) {
        if (canDivert()) {
            HWSystem.Heat heat = hwSystem.heatWater(ctx.minuteOfDay, prevTemp, availableKWh);
            nowTemp = heat.temperature;
            return heat.kWhUsed;
        }
        return 0d;
    }

    @Override
    public OutputChannel divertChannel() {
        return OutputChannel.DIV_TO_WATER;
    }

    /** Promotes this interval's temperature to the carry and returns it (for the WATER_TEMP column). */
    public double commitWaterTemp() {
        waterTemp = nowTemp;
        return nowTemp;
    }

    /**
     * Whether hot water heating is scheduled for the given time. Single source of truth — {@code
     * ScenarioInputs.isHotWaterHeatingScheduled} delegates here. Day-of-week 7 (Sun) normalises to 0.
     * {@code dayOfMonth <= 0} disables the v16 date-window check; the minute window uses the
     * effective minutes, which reproduce the legacy whole-hour exclusive-end check exactly when unset.
     */
    public static boolean isHotWaterHeatingScheduled(List<HWSchedule> hwSchedules, int dayOfWeek,
                                                     int monthOfYear, int dayOfMonth, int minuteOfDay) {
        if (dayOfWeek == 7) dayOfWeek = 0;
        if (!(null == hwSchedules)) for (HWSchedule hwSchedule : hwSchedules) {
            if (hwSchedule.getMonths().months.contains(monthOfYear) &&
                    hwSchedule.getDays().ints.contains(dayOfWeek) &&
                    ScheduleDateWindow.contains(hwSchedule.getStartDate(), hwSchedule.getEndDate(),
                            monthOfYear, dayOfMonth) &&
                    hwSchedule.getEffectiveBeginMinute() <= minuteOfDay &&
                    hwSchedule.getEffectiveEndMinute() > minuteOfDay) {
                return true;
            }
        }
        return false;
    }

    /** Date-blind legacy lookup — kept for callers that have no interval date. */
    public static boolean isHotWaterHeatingScheduled(List<HWSchedule> hwSchedules,
                                                     int dayOfWeek, int monthOfYear, int minuteOfDay) {
        return isHotWaterHeatingScheduled(hwSchedules, dayOfWeek, monthOfYear, 0, minuteOfDay);
    }
}
