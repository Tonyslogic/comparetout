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

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * Shared setup for the backend characterization suite
 * (plans/source/mega-refactor.md, Phase 0): a fresh in-memory Room DB per
 * test wrapped in a real {@link ToutcRepository} via its package-private test
 * seam, so every assertion goes through the public repository API — the
 * surface that is frozen across the DAO/repository split.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public abstract class CharacterizationTestBase {

    protected ToutcDB db;
    protected ToutcRepository repo;

    @Before
    public void characterizationSetUp() {
        db = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), ToutcDB.class)
                .allowMainThreadQueries().build();
        repo = new ToutcRepository(db);
    }

    @After
    public void characterizationTearDown() {
        // Drain in-flight async writes before closing, or a late task hits a
        // closed DB and poisons the shared executor for the next test.
        awaitDbWrites();
        db.close();
    }

    /**
     * Block until every task already queued on {@code ToutcDB.databaseWriteExecutor}
     * has completed. Repository writes are fire-and-forget onto a fixed pool,
     * so tests must quiesce it before asserting. Parking a barrier task on
     * every pool thread guarantees all earlier-queued work has finished.
     */
    protected static void awaitDbWrites() {
        int threads = 8;
        try {
            Field f = ToutcDB.class.getDeclaredField("NUMBER_OF_THREADS");
            f.setAccessible(true);
            threads = f.getInt(null);
        } catch (ReflectiveOperationException ignored) {
            // Field renamed? Fall back to the documented pool size.
        }
        CyclicBarrier barrier = new CyclicBarrier(threads + 1);
        for (int i = 0; i < threads; i++) {
            ToutcDB.databaseWriteExecutor.execute(() -> awaitQuietly(barrier));
        }
        awaitQuietly(barrier);
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("DB write executor did not quiesce", e);
        }
    }
}
