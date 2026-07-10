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

package com.tfcode.comparetout.dynamic.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One day's dispatch plan: grid-charge windows (→ LoadShift rows) and forced
 * export windows (→ DischargeToGrid rows), plus the strategy's forward-model
 * estimate of the end-of-day SOC so the year runner can carry state.
 */
public final class DayDecisions {

    /** A minute-of-day window; {@code endMinute} is exclusive (engine semantics). */
    public static final class Window {
        public final int beginMinute;
        public final int endMinute;
        /** Charge: stop charging at this SOC %; discharge: stop exporting at this SOC %. */
        public final double stopAtPercent;
        /** Discharge rate, kW (0 for charge windows — the battery's own model applies). */
        public final double rateKw;

        public Window(int beginMinute, int endMinute, double stopAtPercent, double rateKw) {
            this.beginMinute = beginMinute;
            this.endMinute = endMinute;
            this.stopAtPercent = stopAtPercent;
            this.rateKw = rateKw;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Window)) return false;
            Window w = (Window) o;
            return beginMinute == w.beginMinute && endMinute == w.endMinute
                    && Double.compare(stopAtPercent, w.stopAtPercent) == 0
                    && Double.compare(rateKw, w.rateKw) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(beginMinute, endMinute, stopAtPercent, rateKw);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "[%d..%d) stopAt=%.1f rate=%.2f",
                    beginMinute, endMinute, stopAtPercent, rateKw);
        }
    }

    public final List<Window> chargeWindows;
    public final List<Window> dischargeWindows;
    /** Forward-model estimate of SOC at midnight, kWh (carried to the next day). */
    public final double socEndKwh;

    public DayDecisions(List<Window> chargeWindows, List<Window> dischargeWindows,
                        double socEndKwh) {
        this.chargeWindows = Collections.unmodifiableList(new ArrayList<>(chargeWindows));
        this.dischargeWindows = Collections.unmodifiableList(new ArrayList<>(dischargeWindows));
        this.socEndKwh = socEndKwh;
    }

    public boolean isEmpty() {
        return chargeWindows.isEmpty() && dischargeWindows.isEmpty();
    }

    /**
     * Merge a 48-slot half-hourly mask into contiguous minute windows.
     * Slot i covers minutes [i*30, i*30+30).
     */
    public static List<Window> toWindows(boolean[] slots, double stopAtPercent, double rateKw) {
        List<Window> windows = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= slots.length; i++) {
            boolean active = i < slots.length && slots[i];
            if (active && start < 0) start = i;
            if (!active && start >= 0) {
                windows.add(new Window(start * 30, i * 30, stopAtPercent, rateKw));
                start = -1;
            }
        }
        return windows;
    }
}
