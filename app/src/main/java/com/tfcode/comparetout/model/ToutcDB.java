/*
 * Copyright (c) 2023. Tony Finnerty
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

import android.content.Context;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
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
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
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
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;

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
        EVDivert.class, Scenario2EVDivert.class,
        LoadProfileData.class, ScenarioSimulationData.class,
        Costings.class, PanelData.class,
        AlphaESSRawPower.class, AlphaESSRawEnergy.class,
        AlphaESSTransformedData.class
        }, version = 3,
        autoMigrations = {
            @AutoMigration(from = 1, to = 2),
            @AutoMigration(from = 2, to = 3)})

@TypeConverters({Converters.class})

public abstract class ToutcDB extends RoomDatabase {

    public abstract PricePlanDAO pricePlanDAO();
    public abstract ScenarioDAO scenarioDAO();
    public abstract CostingDAO costingDAO();
    public abstract AlphaEssDAO alphaEssDAO();

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
