package com.tfcode.comparetout.model;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {PricePlan.class, DayRate.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class ToutcDB extends RoomDatabase {

    public abstract PricePlanDAO pricePlanDAO();

    private static volatile ToutcDB INSTANCE;
    private static final int NUMBER_OF_THREADS = 8;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static ToutcDB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ToutcDB.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    ToutcDB.class, "toutc_database").setQueryExecutor(databaseWriteExecutor)
                            .build();
//                            .addCallback(sRoomDatabaseCallback).build();
                }
            }
        }
        return INSTANCE;
    }
}
