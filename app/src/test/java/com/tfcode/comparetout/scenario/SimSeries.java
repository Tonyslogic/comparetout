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

import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Builds {@link SimulationInputData} series for tests.
 *
 * <p>Phase 0 test-support (see {@code plans/sim/refactor.md}). Generalises the per-test
 * {@code createSID()} helpers into a reusable builder that fills the time fields
 * ({@code date}, {@code minute}, {@code mod}, {@code dow}, {@code do2001}) consistently from a
 * starting {@link LocalDateTime} advancing in 5-minute steps — the same cadence the production
 * simulation uses.</p>
 *
 * <p>Note: the current engine keys rows by these fields rather than by milliseconds-since-epoch;
 * the refactor will move to a millis time axis (Phase 1). Keeping these series honest now means the
 * golden-master snapshots remain meaningful across that change.</p>
 */
public final class SimSeries {

    /** Default start used by the convenience overloads: midsummer, well clear of the DST window. */
    public static final LocalDateTime DEFAULT_START = LocalDateTime.of(2001, 6, 15, 0, 0);

    private static final LocalDate EPOCH_2001 = LocalDate.of(2001, 1, 1);

    private SimSeries() {
    }

    /** A flat series of {@code rows} intervals with constant load and PV, starting at {@link #DEFAULT_START}. */
    public static List<SimulationInputData> constant(int rows, double load, double pv) {
        double[] loads = new double[rows];
        double[] pvs = new double[rows];
        for (int i = 0; i < rows; i++) {
            loads[i] = load;
            pvs[i] = pv;
        }
        return of(DEFAULT_START, loads, pvs);
    }

    /**
     * A series whose load and PV are computed per interval from the interval index, starting at
     * {@link #DEFAULT_START}. Useful for a daily PV bell-curve against a baseline load.
     */
    public static List<SimulationInputData> generated(int rows, DoubleUnaryOperator loadFn, DoubleUnaryOperator pvFn) {
        double[] loads = new double[rows];
        double[] pvs = new double[rows];
        for (int i = 0; i < rows; i++) {
            loads[i] = loadFn.applyAsDouble(i);
            pvs[i] = pvFn.applyAsDouble(i);
        }
        return of(DEFAULT_START, loads, pvs);
    }

    /** A series from explicit load and PV arrays (which must be the same length), starting at {@link #DEFAULT_START}. */
    public static List<SimulationInputData> of(double[] load, double[] pv) {
        return of(DEFAULT_START, load, pv);
    }

    /** A series from explicit load and PV arrays starting at the given time, advancing 5 minutes per row. */
    public static List<SimulationInputData> of(LocalDateTime start, double[] load, double[] pv) {
        if (load.length != pv.length) {
            throw new IllegalArgumentException("load and pv arrays must be the same length");
        }
        List<SimulationInputData> series = new ArrayList<>(load.length);
        for (int i = 0; i < load.length; i++) {
            LocalDateTime t = start.plusMinutes(5L * i);
            SimulationInputData sid = new SimulationInputData();
            sid.setDate(t.toLocalDate().toString());
            sid.setMinute(String.format("%02d:%02d", t.getHour(), t.getMinute()));
            sid.setMod(t.getHour() * 60 + t.getMinute());
            sid.setDow(t.getDayOfWeek().getValue()); // 1 (Mon) .. 7 (Sun), matching LoadProfileData
            sid.setDo2001((int) ChronoUnit.DAYS.between(EPOCH_2001, t.toLocalDate()) + 1);
            sid.setLoad(load[i]);
            sid.setTpv(pv[i]);
            series.add(sid);
        }
        return series;
    }

    /** Deep copy of a series, so each inverter in a multi-inverter scenario holds its own rows. */
    public static List<SimulationInputData> copyOf(List<SimulationInputData> source) {
        List<SimulationInputData> copy = new ArrayList<>(source.size());
        for (SimulationInputData s : source) {
            SimulationInputData c = new SimulationInputData();
            c.setDate(s.getDate());
            c.setMinute(s.getMinute());
            c.setMod(s.getMod());
            c.setDow(s.getDow());
            c.setDo2001(s.getDo2001());
            c.setLoad(s.getLoad());
            c.setTpv(s.getTpv());
            copy.add(c);
        }
        return copy;
    }
}
