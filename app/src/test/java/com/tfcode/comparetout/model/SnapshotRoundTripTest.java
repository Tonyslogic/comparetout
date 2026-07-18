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

package com.tfcode.comparetout.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.testdata.EnergyDataGenerator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Exercises the portable-{@code .db} mechanism that {@link SnapshotExporter} /
 * {@link SnapshotImporter} rely on: a standalone SQLite file opened <em>as a
 * {@link ToutcDB}</em>, which Room gates on its schema-identity hash (a
 * mismatched or corrupt file fails to open). A month of generated data is
 * written to such a file, the file is reopened fresh, and the aggregates are
 * asserted to survive the file boundary intact — the realistic-data transport
 * path, without committing a binary fixture (the file is generated per run)
 * and without dragging in the full {@code SnapshotImporter.commit} app/DataStore
 * stack (better covered by an instrumented test if ever needed).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SnapshotRoundTripTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");
    private static final double DELTA = 1e-6;

    @Test
    public void monthOfDataRoundTripsThroughAStandaloneDbFile() {
        Context context = ApplicationProvider.getApplicationContext();
        File snapshot = new File(context.getCacheDir(), "snapshot-roundtrip.db");
        //noinspection ResultOfMethodCallIgnored
        snapshot.delete();

        String sysSn = "Solis-SNAP";
        LocalDate from = LocalDate.of(2001, 6, 1);
        LocalDate to = LocalDate.of(2001, 6, 30);
        List<AlphaESSTransformedData> rows = EnergyDataGenerator.generate(sysSn, from, to, DUBLIN);
        double expectedPv = rows.stream().mapToDouble(AlphaESSTransformedData::getPv).sum();
        double expectedBuy = rows.stream().mapToDouble(AlphaESSTransformedData::getBuy).sum();

        // Write: an empty schema-correct ToutcDB file (the SnapshotExporter trick),
        // seeded with the generated month.
        ToutcDB writeDb = Room.databaseBuilder(context, ToutcDB.class, snapshot.getAbsolutePath())
                .allowMainThreadQueries().build();
        writeDb.alphaEssDAO().addTransformedData(rows);
        writeDb.close();

        assertTrue("snapshot file should exist", snapshot.exists());
        // A month of one source is small — this is the anti-bloat point.
        assertTrue("a month of 5-min data should be well under 5 MB (was "
                        + snapshot.length() + " bytes)", snapshot.length() < 5 * 1024 * 1024);

        // Reopen the SAME file as a ToutcDB — Room validates the schema-identity
        // hash on open (the gate SnapshotImporter.validate depends on).
        ToutcDB readDb = Room.databaseBuilder(context, ToutcDB.class, snapshot.getAbsolutePath())
                .allowMainThreadQueries().build();
        List<IntervalRow> hourly = readDb.alphaEssDAO()
                .sumHour(sysSn, from.toString(), to.toString());
        double roundTrippedPv = hourly.stream().mapToDouble(r -> r.pv).sum();
        double roundTrippedBuy = hourly.stream().mapToDouble(r -> r.buy).sum();
        readDb.close();

        assertEquals(expectedPv, roundTrippedPv, DELTA);
        assertEquals(expectedBuy, roundTrippedBuy, DELTA);

        //noinspection ResultOfMethodCallIgnored
        snapshot.delete();
    }
}
