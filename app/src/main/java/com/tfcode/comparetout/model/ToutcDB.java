package com.tfcode.comparetout.model;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {
        PricePlan.class, DayRate.class,
        Scenario.class,
        Inverter.class, Scenario2Inverter.class,
        Battery.class, Scenario2Battery.class,
        Panel.class, Scenario2Panel.class,
        HWSystem.class, Scenario2HWSystem.class,
        LoadProfile.class, Scenario2LoadProfile.class,
        LoadShift.class, Scenario2LoadShift.class,
        EVCharge.class, Scenario2EVCharge.class,
        HWSchedule.class, Scenario2HWSchedule.class,
        HWDivert.class, Scenario2HWDivert.class,
        EVDivert.class, Scenario2EVDivert.class
        }, version = 1)

@TypeConverters({Converters.class})

public abstract class ToutcDB extends RoomDatabase {

    public abstract PricePlanDAO pricePlanDAO();
    public abstract ScenarioDAO sceanrioDAO();

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
                }
            }
        }
        return INSTANCE;
    }
}
