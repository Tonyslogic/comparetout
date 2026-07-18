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

    private static final int LATEST_VERSION = 16;
    private static final File V1_SCHEMA =
            new File("schemas/com.tfcode.comparetout.model.ToutcDB/1.json");

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
        // chain to v16 and validates the final schema identity. getWritableDatabase
        // forces that to happen synchronously here.
        ToutcDB room = Room.databaseBuilder(context, ToutcDB.class, dbFile.getAbsolutePath())
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
