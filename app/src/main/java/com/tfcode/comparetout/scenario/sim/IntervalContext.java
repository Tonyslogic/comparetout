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
 * Read-only, per-interval context passed to {@link SimComponent} capabilities.
 *
 * <p>Component-registration refactor (see {@code plans/sim/component.md}). The engine resolves the
 * canonical UTC instant for an interval once inside {@code processOneRow} and exposes the derived
 * time-of-day fields here, so components do not re-derive them and never see inverter/battery/bus
 * internals — they are told only when they are and how much surplus/demand is in play.</p>
 *
 * <p>{@link #intervalHours} is the interval length as a fraction of an hour (1/12 for the 5-minute
 * grid). It is informational: components that must stay byte-identical with the legacy engine should
 * reproduce its exact arithmetic (e.g. {@code draw / 12d}) rather than multiplying by this value,
 * since {@code x / 12d} and {@code x * (1d/12d)} are not equal in IEEE-754.</p>
 */
public final class IntervalContext {

    /** Canonical UTC instant for this interval (epoch millis). */
    public final long millis;
    /** Month of year, 1 (Jan) .. 12 (Dec). */
    public final int month;
    /** Day of week, 1 (Mon) .. 7 (Sun), matching the schedule model. */
    public final int dayOfWeek;
    /** Minute of day, 0 .. 1439. */
    public final int minuteOfDay;
    /** UTC epoch-day, the key for the per-day EV-divert accumulator. */
    public final int evDivertDay;
    /** Interval length as a fraction of an hour (1/12 for the 5-minute grid). */
    public final double intervalHours;

    public IntervalContext(long millis, int month, int dayOfWeek, int minuteOfDay,
                           int evDivertDay, double intervalHours) {
        this.millis = millis;
        this.month = month;
        this.dayOfWeek = dayOfWeek;
        this.minuteOfDay = minuteOfDay;
        this.evDivertDay = evDivertDay;
        this.intervalHours = intervalHours;
    }
}
