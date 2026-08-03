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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Guards the Room AutoMigration chain (schema v1 → v16) against silent
 * back-slips. It builds a genuine v1 database from the committed
 * {@code schemas/…/1.json}, seeds it with real data, then opens it through
 * {@link ToutcDB} so Room applies every auto-migration and validates the
 * resulting schema against the compiled-in v16 identity hash (a migration
 * that produced the wrong schema throws on open). The seeded row must survive.
 *
 * <p>Runs on the JVM under Robolectric — no device, no FTL — so it rides the
 * existing {@code testIeDebugUnitTest} CI gate. The v1 schema is read straight
 * from the filesystem (like {@code GoldenMaster} reads {@code src/test/resources}),
 * which avoids the AGP unit-test asset-merge limitation that keeps test-only
 * assets out of Robolectric's reach.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DbMigrationTest {

    private static final int LATEST_VERSION = 17;
    private static final String SCHEMA_DIR = "schemas/com.tfcode.comparetout.model.ToutcDB/";
    private static final File V1_SCHEMA = new File(SCHEMA_DIR + "1.json");
    private static final File V16_SCHEMA = new File(SCHEMA_DIR + "16.json");

    @Test
    public void migratesV1DataAllTheWayToLatest() throws Exception {
        assertTrue("v1 schema JSON must be committed at " + V1_SCHEMA.getPath(),
                V1_SCHEMA.exists());
        Context context = ApplicationProvider.getApplicationContext();
        File dbFile = new File(context.getCacheDir(), "migration-v1.db");
        //noinspection ResultOfMethodCallIgnored
        dbFile.delete();

        buildVersion1Database(dbFile);

        // Open through Room: user_version is 1, so Room runs the auto-migration
        // chain and then the hand-written 16→17, and validates the final schema
        // identity. getWritableDatabase forces that to happen synchronously here.
        ToutcDB room = Room.databaseBuilder(context, ToutcDB.class, dbFile.getAbsolutePath())
                .addMigrations(ToutcDB.MIGRATION_16_17)
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase migrated = room.getOpenHelper().getWritableDatabase();

        assertEquals("should have migrated to the latest schema version",
                LATEST_VERSION, migrated.getVersion());
        try (Cursor c = migrated.query(
                "SELECT supplier, planName FROM PricePlans WHERE reference = 'migration-test'")) {
            assertEquals("seeded row must survive every migration", 1, c.getCount());
            c.moveToFirst();
            assertEquals("TestCo", c.getString(0));
            assertEquals("Flat", c.getString(1));
        }
        room.close();
        //noinspection ResultOfMethodCallIgnored
        dbFile.delete();
    }

    /**
     * v16 → v17 specifically: the hand-written migration that recreates
     * {@code costings} around a three-column primary key. Every existing costing
     * must survive with {@code exportPlanID = 0} — the bundled sentinel, whose
     * meaning is exactly what a pre-v17 row already had — and the widened
     * PricePlans unique key must admit a same-named export plan.
     */
    @Test
    public void migratesV16CostingsOntoTheExportPlanKey() throws Exception {
        assertTrue("v16 schema JSON must be committed at " + V16_SCHEMA.getPath(),
                V16_SCHEMA.exists());
        Context context = ApplicationProvider.getApplicationContext();
        File dbFile = new File(context.getCacheDir(), "migration-v16.db");
        //noinspection ResultOfMethodCallIgnored
        dbFile.delete();

        buildDatabaseFromSchema(V16_SCHEMA, 16, raw -> {
            raw.execSQL("INSERT INTO PricePlans "
                    + "(pricePlanIndex, supplier, planName, feed, standingCharges, "
                    + " signUpBonus, reference, active, lastUpdate, location) "
                    + "VALUES (1, 'TestCo', 'Flat', 15.0, 0.0, 0.0, 'v16-test', 1, "
                    + "        '2026-01-01', '')");
            raw.execSQL("INSERT INTO costings "
                    + "(scenarioID, pricePlanID, buy, sell, net, scenarioName, fullPlanName) "
                    + "VALUES (42, 1, 1234.5, 200.0, 1034.5, 'Sim A', 'TestCo:Flat')");
        }, dbFile);

        ToutcDB room = Room.databaseBuilder(context, ToutcDB.class, dbFile.getAbsolutePath())
                .addMigrations(ToutcDB.MIGRATION_16_17)
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase migrated = room.getOpenHelper().getWritableDatabase();

        assertEquals(LATEST_VERSION, migrated.getVersion());

        try (Cursor c = migrated.query("SELECT exportPlanID, buy, sell, net, fullPlanName "
                + "FROM costings WHERE scenarioID = 42 AND pricePlanID = 1")) {
            assertEquals("the v16 costing must survive", 1, c.getCount());
            c.moveToFirst();
            assertEquals("legacy rows carry the bundled sentinel", 0, c.getLong(0));
            assertEquals(1234.5, c.getDouble(1), 1e-9);
            assertEquals(200.0, c.getDouble(2), 1e-9);
            assertEquals(1034.5, c.getDouble(3), 1e-9);
            assertEquals("TestCo:Flat", c.getString(4));
        }

        // Pre-v17 plans are import plans.
        try (Cursor c = migrated.query(
                "SELECT direction, compatibleWith FROM PricePlans WHERE pricePlanIndex = 1")) {
            c.moveToFirst();
            assertEquals(0, c.getLong(0));
            assertTrue("no tags on a migrated import plan", c.isNull(1));
        }

        // The widened unique key admits an export plan with the same name — the
        // whole point of the index change. Under the v16 key this INSERT failed.
        migrated.execSQL("INSERT INTO PricePlans "
                + "(supplier, planName, feed, standingCharges, signUpBonus, reference, "
                + " active, lastUpdate, location, direction) "
                + "VALUES ('TestCo', 'Flat', 0.0, 0.0, 0.0, 'export', 1, '2026-01-01', '', 1)");
        try (Cursor c = migrated.query(
                "SELECT COUNT(*) FROM PricePlans WHERE supplier = 'TestCo' AND planName = 'Flat'")) {
            c.moveToFirst();
            assertEquals("import and export plan may share a name", 2, c.getLong(0));
        }

        // The new pairings table exists and round-trips.
        migrated.execSQL("INSERT INTO plan_combinations (importPlanID, exportPlanID, source) "
                + "VALUES (1, 2, 'manual')");
        try (Cursor c = migrated.query("SELECT source FROM plan_combinations "
                + "WHERE importPlanID = 1 AND exportPlanID = 2")) {
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals("manual", c.getString(0));
        }

        room.close();
        //noinspection ResultOfMethodCallIgnored
        dbFile.delete();
    }

    /** Seeds rows into a freshly-built database at a known schema version. */
    private interface Seeder {
        void seed(SQLiteDatabase raw);
    }

    /** Recreate a database at the exact shape of an exported schema JSON, then seed it. */
    private void buildDatabaseFromSchema(File schema, int version, Seeder seeder, File dbFile)
            throws Exception {
        String json = new String(Files.readAllBytes(schema.toPath()), StandardCharsets.UTF_8);
        JsonObject database = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("database");

        SQLiteDatabase raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        try {
            for (JsonElement entityEl : database.getAsJsonArray("entities")) {
                JsonObject entity = entityEl.getAsJsonObject();
                String table = entity.get("tableName").getAsString();
                raw.execSQL(withTableName(entity.get("createSql").getAsString(), table));
                JsonArray indices = entity.getAsJsonArray("indices");
                if (indices != null) {
                    for (JsonElement idxEl : indices) {
                        raw.execSQL(withTableName(
                                idxEl.getAsJsonObject().get("createSql").getAsString(), table));
                    }
                }
            }
            for (JsonElement q : database.getAsJsonArray("setupQueries")) {
                raw.execSQL(q.getAsString());
            }
            seeder.seed(raw);
            raw.setVersion(version);
        } finally {
            raw.close();
        }
    }

    /** Recreate the exact v1 database from its exported schema JSON, then seed one row. */
    private void buildVersion1Database(File dbFile) throws Exception {
        String json = new String(Files.readAllBytes(V1_SCHEMA.toPath()), StandardCharsets.UTF_8);
        JsonObject database = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("database");

        SQLiteDatabase raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        try {
            for (JsonElement entityEl : database.getAsJsonArray("entities")) {
                JsonObject entity = entityEl.getAsJsonObject();
                String table = entity.get("tableName").getAsString();
                raw.execSQL(withTableName(entity.get("createSql").getAsString(), table));
                JsonArray indices = entity.getAsJsonArray("indices");
                if (indices != null) {
                    for (JsonElement idxEl : indices) {
                        raw.execSQL(withTableName(
                                idxEl.getAsJsonObject().get("createSql").getAsString(), table));
                    }
                }
            }
            // room_master_table + v1 identity hash so Room recognises the start point.
            for (JsonElement q : database.getAsJsonArray("setupQueries")) {
                raw.execSQL(q.getAsString());
            }
            raw.execSQL("INSERT INTO PricePlans "
                    + "(supplier, planName, feed, standingCharges, signUpBonus, reference, active) "
                    + "VALUES ('TestCo', 'Flat', 15.0, 0.0, 0.0, 'migration-test', 1)");
            raw.setVersion(1);
        } finally {
            raw.close();
        }
    }

    private static String withTableName(String createSql, String table) {
        return createSql.replace("${TABLE_NAME}", table);
    }
}
