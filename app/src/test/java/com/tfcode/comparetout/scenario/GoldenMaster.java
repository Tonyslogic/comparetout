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

import static org.junit.Assert.fail;

import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Golden-master (characterization / approval) harness for the simulation engine.
 *
 * <p>Phase 0 of the refactor (see {@code plans/sim/refactor.md}). The point of a golden master is to
 * pin the simulation's <em>current</em> output — bugs and all — so that:</p>
 * <ul>
 *   <li>refactors that are meant to preserve behaviour (Phase 1) can be proven safe: the approved
 *       files stay byte-for-byte identical;</li>
 *   <li>intended behaviour changes (the load-sharing fix in Phase 2, the hybrid DC/AC bus model in
 *       Phase 3) show up as a deliberate, reviewable diff in the approved files.</li>
 * </ul>
 *
 * <p><b>Workflow.</b> Each scenario serialises its output to a canonical CSV and calls
 * {@link #verify(String, String)}. On the first run no {@code .approved.csv} exists, so the harness
 * writes one and passes — this <em>seeds</em> the baseline. The seeded files are committed after
 * review. On every later run the output is compared against the approved file; a mismatch writes a
 * {@code .received.csv} alongside it and fails the test, pointing at the first differing line. When a
 * change is intended, delete (or overwrite) the approved file, re-run to re-seed, and review the diff.</p>
 *
 * <p>Files live under {@code app/src/test/resources/sim-golden/} relative to the module working
 * directory used by the Gradle test task.</p>
 */
public final class GoldenMaster {

    private static final File DIR = new File("src/test/resources/sim-golden");

    /** Decimal places retained when serialising energy values, to avoid floating-point noise. */
    private static final int PRECISION = 6;

    private static final String HEADER =
            "row,date,mod,dow,do2001,millis,load,pv,buy,feed,soc,"
                    + "pv2charge,pv2load,bat2load,grid2bat,bat2grid,directEV,divEV,divWater,immersion,waterTemp";

    private GoldenMaster() {
    }

    /** Serialise simulation output rows to a stable, diff-friendly CSV string. */
    public static String serialize(List<ScenarioSimulationData> rows) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < rows.size(); i++) {
            ScenarioSimulationData r = rows.get(i);
            sb.append(i).append(',')
                    .append(r.getDate()).append(',')
                    .append(r.getMinuteOfDay()).append(',')
                    .append(r.getDayOfWeek()).append(',')
                    .append(r.getDayOf2001()).append(',')
                    .append(r.getMillisSinceEpoch() == null ? "null" : r.getMillisSinceEpoch().toString()).append(',')
                    .append(f(r.getLoad())).append(',')
                    .append(f(r.getPv())).append(',')
                    .append(f(r.getBuy())).append(',')
                    .append(f(r.getFeed())).append(',')
                    .append(f(r.getSOC())).append(',')
                    .append(f(r.getPvToCharge())).append(',')
                    .append(f(r.getPvToLoad())).append(',')
                    .append(f(r.getBatToLoad())).append(',')
                    .append(f(r.getGridToBattery())).append(',')
                    .append(f(r.getBattery2Grid())).append(',')
                    .append(f(r.getDirectEVcharge())).append(',')
                    .append(f(r.getKWHDivToEV())).append(',')
                    .append(f(r.getKWHDivToWater())).append(',')
                    .append(f(r.getImmersionLoad())).append(',')
                    .append(f(r.getWaterTemp())).append('\n');
        }
        return sb.toString();
    }

    /**
     * Compare {@code actual} against the approved snapshot for {@code name}, seeding it on first run.
     *
     * @param name a stable identifier for the scenario (used as the file base name)
     * @param actual the serialised current output
     */
    public static void verify(String name, String actual) {
        try {
            if (!DIR.exists() && !DIR.mkdirs()) {
                fail("Could not create golden directory: " + DIR.getAbsolutePath());
            }
            File approved = new File(DIR, name + ".approved.csv");
            File received = new File(DIR, name + ".received.csv");
            if (!approved.exists()) {
                Files.write(approved.toPath(), actual.getBytes(StandardCharsets.UTF_8));
                System.out.println("[golden] SEEDED baseline for '" + name + "' at "
                        + approved.getAbsolutePath() + " — review and commit it.");
                return;
            }
            String expected = new String(Files.readAllBytes(approved.toPath()), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            String normalisedActual = actual.replace("\r\n", "\n");
            if (!expected.equals(normalisedActual)) {
                Files.write(received.toPath(), normalisedActual.getBytes(StandardCharsets.UTF_8));
                fail("Golden mismatch for '" + name + "' " + firstDifference(expected, normalisedActual)
                        + "\n  approved: " + approved.getAbsolutePath()
                        + "\n  received: " + received.getAbsolutePath()
                        + "\n  If this change is intended, delete the approved file, re-run to re-seed, and review the diff.");
            } else if (received.exists()) {
                // Clean up a stale received file from a previous failing run.
                //noinspection ResultOfMethodCallIgnored
                received.delete();
            }
        } catch (IOException e) {
            fail("Golden I/O error for '" + name + "': " + e.getMessage());
        }
    }

    private static String firstDifference(String expected, String actual) {
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        int n = Math.min(e.length, a.length);
        for (int i = 0; i < n; i++) {
            if (!e[i].equals(a[i])) {
                return "at line " + (i + 1) + ":\n  expected: " + e[i] + "\n  actual:   " + a[i];
            }
        }
        if (e.length != a.length) {
            return "(line count differs: approved=" + e.length + ", actual=" + a.length + ")";
        }
        return "(no line-level difference found)";
    }

    private static String f(double v) {
        // Normalise -0.0 to 0.0 so the output is stable across equivalent computations.
        if (v == 0.0) {
            v = 0.0;
        }
        return String.format(Locale.ROOT, "%." + PRECISION + "f", v);
    }
}
