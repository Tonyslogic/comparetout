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

import androidx.annotation.NonNull;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawEnergy;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSRawPower;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PlanCombination;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.Scenario2Battery;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWDivert;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2HeatPump;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.model.scenario.ScenarioReadiness;
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
        DischargeToGrid.class, Scenario2DischargeToGrid.class,
        EVCharge.class, Scenario2EVCharge.class,
        HWSchedule.class, Scenario2HWSchedule.class,
        HWDivert.class, Scenario2HWDivert.class,
        EVDivert.class, Scenario2EVDivert.class,
        HeatPump.class, Scenario2HeatPump.class,
        LoadProfileData.class, ScenarioSimulationData.class,
        Costings.class, PanelData.class,
        AlphaESSRawPower.class, AlphaESSRawEnergy.class,
        AlphaESSTransformedData.class,
        AlphaESSTransformMeta.class,
        ScenarioReadiness.class,
        PlanCombination.class
        }, version = 17,
        autoMigrations = {
            @AutoMigration(from = 1, to = 2),
            @AutoMigration(from = 2, to = 3),
            @AutoMigration(from = 3, to = 4),
            @AutoMigration(from = 4, to = 5),
            @AutoMigration(from = 5, to = 6),
            @AutoMigration(from = 6, to = 7),
            @AutoMigration(from = 7, to = 8),
            @AutoMigration(from = 8, to = 9),
            @AutoMigration(from = 9, to = 10),
            @AutoMigration(from = 10, to = 11),
            @AutoMigration(from = 11, to = 12),
            @AutoMigration(from = 12, to = 13),
            @AutoMigration(from = 13, to = 14),
            @AutoMigration(from = 14, to = 15),
            @AutoMigration(from = 15, to = 16)})

@TypeConverters({Converters.class})

/*
  Room database configuration for the TOUTC application.
  <p>
  This abstract class defines the central database structure using Android's Room
  persistence library, managing all energy system data, user scenarios, pricing
  information, and calculation results. The database uses a comprehensive entity
  model that captures the complex relationships between energy system components.
  <p>
  Key entity categories:
  - Price Plans: Electricity tariffs, rates, and billing structures
  - Scenarios: User-defined energy system configurations
  - Components: Inverters, batteries, solar panels, and load profiles
  - Associations: Many-to-many relationships between scenarios and components
  - Simulation Data: Time-series energy flow calculations
  - Cost Analysis: Financial calculations and comparison results
  - Import Data: Raw and processed data from external energy systems
  <p>
  The database employs automatic migrations to handle schema evolution gracefully,
  ensuring user data is preserved across application updates. A dedicated thread
  pool provides efficient concurrent access for database operations while
  maintaining data integrity through Room's built-in synchronization.
  <p>
  Database access is coordinated through specialized DAO (Data Access Object)
  interfaces that encapsulate query logic and provide type-safe database operations
  with LiveData support for reactive UI updates.
 */
public abstract class ToutcDB extends RoomDatabase {

    /**
     * Data Access Object for price plan and tariff operations.
     * 
     * @return DAO instance for managing electricity pricing data
     */
    public abstract PricePlanDAO pricePlanDAO();
    
    /**
     * Data Access Object for energy system scenario operations.
     * 
     * @return DAO instance for managing user scenarios and components
     */
    public abstract ScenarioDAO scenarioDAO();
    
    /**
     * Data Access Object for cost calculation and analysis operations.
     * 
     * @return DAO instance for managing financial comparison data
     */
    public abstract CostingDAO costingDAO();
    
    /**
     * Data Access Object for AlphaESS energy system integration.
     * 
     * @return DAO instance for managing imported energy system data
     */
    public abstract AlphaEssDAO alphaEssDAO();

    /**
     * Data Access Object for inverter-domain queries (mega-refactor C1).
     *
     * @return DAO instance for inverter queries; orchestration lives in
     *         {@link com.tfcode.comparetout.model.ops.InverterOps}
     */
    public abstract com.tfcode.comparetout.model.dao.InverterDAO inverterDAO();

    /**
     * Data Access Object for battery / load-shift / discharge queries
     * (mega-refactor C2). Orchestration lives in
     * {@link com.tfcode.comparetout.model.ops.BatteryOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.BatteryDAO batteryDAO();

    /**
     * Data Access Object for hot-water (system + schedule) queries
     * (mega-refactor C3). Orchestration lives in
     * {@link com.tfcode.comparetout.model.ops.HotWaterOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.HotWaterDAO hotWaterDAO();

    /**
     * Data Access Object for EV (charge + divert) queries (mega-refactor C4).
     * Orchestration lives in {@link com.tfcode.comparetout.model.ops.EvOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.EvDAO evDAO();

    /**
     * Data Access Object for heat-pump queries (mega-refactor C5).
     * Orchestration lives in {@link com.tfcode.comparetout.model.ops.HeatPumpOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.HeatPumpDAO heatPumpDAO();

    /**
     * Data Access Object for panel / panel-data queries (mega-refactor C6).
     * Orchestration lives in {@link com.tfcode.comparetout.model.ops.PanelOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.PanelDAO panelDAO();

    /**
     * Data Access Object for load-profile / load-profile-data queries
     * (mega-refactor C7). Orchestration lives in
     * {@link com.tfcode.comparetout.model.ops.LoadProfileOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.LoadProfileDAO loadProfileDAO();

    /**
     * Data Access Object for simulation-output queries (mega-refactor C8).
     */
    public abstract com.tfcode.comparetout.model.dao.SimDataDAO simDataDAO();

    /**
     * Data Access Object for readiness-matrix queries (mega-refactor C8).
     * Terminal-state setters live in
     * {@link com.tfcode.comparetout.model.ops.ReadinessOps}.
     */
    public abstract com.tfcode.comparetout.model.dao.ReadinessDAO readinessDAO();

    /**
     * Data Access Object for ticked (import × export) plan pairings.
     * See {@link com.tfcode.comparetout.model.priceplan.PlanCombination}.
     */
    public abstract com.tfcode.comparetout.model.dao.CombinationDAO combinationDAO();

    /**
     * v16 → v17: separate export contracts (plans/region/import-plans.md §2.3).
     * <p>
     * Hand-written, not an AutoMigration, because {@code costings} gains a third
     * primary-key column and Room cannot infer a key change — and because a
     * version step must be entirely auto or entirely manual, so the two additive
     * {@code PricePlans} columns ride along here too.
     * <p>
     * The DDL is copied verbatim from the exported {@code 17.json}, index names
     * included: Room validates the post-migration schema against its own
     * generated one and throws on any difference.
     * <p>
     * Every existing costing row becomes {@code exportPlanID = 0} — "priced
     * against this import plan's own export side" — which is exactly its old
     * meaning, so bundled-export regions see no change.
     */
    public static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // ── PricePlans: direction + compatibility tags, and a unique key that
            //    now admits an import and an export plan sharing a name.
            db.execSQL("ALTER TABLE `PricePlans` ADD COLUMN `direction` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `PricePlans` ADD COLUMN `compatibleWith` TEXT");
            db.execSQL("DROP INDEX IF EXISTS `index_PricePlans_supplier_planName`");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_PricePlans_supplier_planName_direction` "
                    + "ON `PricePlans` (`supplier`, `planName`, `direction`)");

            // ── plan_combinations: the ticked (import × export) pairings.
            db.execSQL("CREATE TABLE IF NOT EXISTS `plan_combinations` ("
                    + "`importPlanID` INTEGER NOT NULL, "
                    + "`exportPlanID` INTEGER NOT NULL, "
                    + "`source` TEXT, "
                    + "PRIMARY KEY(`importPlanID`, `exportPlanID`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_combinations_exportPlanID` "
                    + "ON `plan_combinations` (`exportPlanID`)");

            // ── costings: recreate with the three-column primary key. SQLite
            //    cannot ALTER a primary key, so this is create/copy/drop/rename.
            db.execSQL("CREATE TABLE IF NOT EXISTS `costings_new` ("
                    + "`scenarioID` INTEGER NOT NULL, "
                    + "`pricePlanID` INTEGER NOT NULL, "
                    + "`exportPlanID` INTEGER NOT NULL DEFAULT 0, "
                    + "`buy` REAL NOT NULL, "
                    + "`sell` REAL NOT NULL, "
                    + "`subTotals` TEXT, "
                    + "`scenarioName` TEXT, "
                    + "`fullPlanName` TEXT, "
                    + "`net` REAL NOT NULL, "
                    + "PRIMARY KEY(`scenarioID`, `pricePlanID`, `exportPlanID`))");
            db.execSQL("INSERT INTO `costings_new` "
                    + "(`scenarioID`, `pricePlanID`, `exportPlanID`, `buy`, `sell`, "
                    + " `subTotals`, `scenarioName`, `fullPlanName`, `net`) "
                    + "SELECT `scenarioID`, `pricePlanID`, 0, `buy`, `sell`, "
                    + "       `subTotals`, `scenarioName`, `fullPlanName`, `net` "
                    + "FROM `costings`");
            db.execSQL("DROP TABLE `costings`");
            db.execSQL("ALTER TABLE `costings_new` RENAME TO `costings`");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_costings_scenarioID_pricePlanID_exportPlanID` "
                    + "ON `costings` (`scenarioID`, `pricePlanID`, `exportPlanID`)");
        }
    };

    /**
     * Every hand-written migration, in one place. Auto-migrations are declared
     * on {@code @Database} and need no registration; these do.
     * <p>
     * Register this on <b>any</b> builder that opens a database the user could
     * have created with an older build — the live database and the snapshot
     * import staging file. A snapshot is a backup, so restoring one taken before
     * a schema change must upgrade it rather than reject it; Room's identity
     * check still refuses anything it cannot reach the current schema from.
     */
    public static final Migration[] MIGRATIONS = { MIGRATION_16_17 };

    private static volatile ToutcDB INSTANCE;
    private static final int NUMBER_OF_THREADS = 8;
    static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * Get the singleton database instance with thread-safe initialization.
     * <p>
     * Uses double-checked locking pattern to ensure thread-safe singleton
     * creation while avoiding synchronization overhead after initialization.
     * The database is configured with a dedicated thread pool for write
     * operations to prevent blocking the main UI thread.
     * 
     * @param context application context for database creation
     * @return the singleton ToutcDB instance
     */
    static ToutcDB getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (ToutcDB.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    ToutcDB.class, "toutc_database").setQueryExecutor(databaseWriteExecutor)
                            .addMigrations(MIGRATIONS)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
