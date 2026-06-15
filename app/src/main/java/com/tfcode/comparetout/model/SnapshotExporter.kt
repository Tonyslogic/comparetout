/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.tfcode.comparetout.model

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Scoped SQLite snapshot writer for the Import / Export screen.
 *
 * Lives in the `com.tfcode.comparetout.model` package so it can call the
 * package-private [ToutcDB.getDatabase] without exposing a public accessor
 * on the live database or editing any existing DAO / repository / model
 * file. Pure Kotlin; no Hilt entry-point of its own.
 *
 * The exporter builds a brand-new SQLite file whose schema is identical to
 * the production database, then `ATTACH`es it to the live database and
 * copies rows in dependency order via `INSERT … SELECT … WHERE`. The
 * resulting file is a complete, schema-versioned `ToutcDB` snapshot that
 * Room can open directly on the import side.
 */
class SnapshotExporter(private val application: Application) {

    /** What the caller asked to export. */
    sealed class Scope {
        /** Whole database — every plan, scenario, source, and (optionally) output row. */
        data object Everything : Scope()

        /** Selected scenarios and/or sources. Plans are always full. */
        data class Selection(
            val scenarioIds: Set<Long>,
            val sysSns: Set<String>
        ) : Scope() {
            val isEmpty: Boolean get() = scenarioIds.isEmpty() && sysSns.isEmpty()
        }
    }

    /**
     * Build [target] as a scoped snapshot of the live database. Deletes the
     * file first if it exists. Caller is responsible for choosing the path
     * (typically `cacheDir/exports/eco-power-optimiser-<...>.db`).
     *
     * @param includeOutputs when true, costings / scenariosimulationdata /
     *   loadprofiledata / paneldata are exported alongside inputs. When false
     *   only the wizard inputs ship — outputs are recomputable from inputs.
     */
    fun buildSnapshot(scope: Scope, includeOutputs: Boolean, target: File) {
        if (scope is Scope.Selection && scope.isEmpty) {
            throw IllegalArgumentException("Selection export requires at least one scenario or source")
        }

        // Prepare a clean destination file.
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Could not overwrite existing snapshot at ${target.absolutePath}")
        }

        // Step 1 — bring the empty target up to the current ToutcDB schema by
        // briefly opening it as a Room database. The .build() + first DB
        // access triggers CREATE TABLE for every entity. We close immediately
        // so the file's handles are released before we ATTACH it.
        val builder = Room.databaseBuilder(application, ToutcDB::class.java, target.absolutePath)
        val temp = builder.build()
        temp.openHelper.writableDatabase.version    // force open
        temp.close()

        // Step 2 — copy rows from the live database into the temp file via
        // ATTACH. Use the live DB's existing connection so we see all WAL
        // writes; ATTACH cannot run inside a transaction so we bracket the
        // copy phase with a transaction *between* ATTACH and DETACH.
        val live = ToutcDB.getDatabase(application)
        val sql = live.openHelper.writableDatabase
        val attachPath = target.absolutePath.replace("'", "''")

        sql.execSQL("ATTACH DATABASE '$attachPath' AS export")
        try {
            sql.beginTransaction()
            try {
                copyAll(sql, scope, includeOutputs)
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
        } finally {
            sql.execSQL("DETACH DATABASE export")
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Row-copy SQL — written against the attached `export` alias. Every
    // table is `SELECT *`; the WHERE clauses scope by scenario or sysSn.
    // ────────────────────────────────────────────────────────────────────

    private fun copyAll(sql: SupportSQLiteDatabase, scope: Scope, includeOutputs: Boolean) {
        // Plans always ship in full so any imported scenario can find its tariff.
        sql.execSQL("INSERT INTO export.PricePlans SELECT * FROM PricePlans")
        sql.execSQL("INSERT INTO export.DayRates  SELECT * FROM DayRates")

        when (scope) {
            is Scope.Everything -> {
                copyAllScenariosBlock(sql, includeOutputs)
                copyAllSources(sql)
            }
            is Scope.Selection -> {
                if (scope.scenarioIds.isNotEmpty()) {
                    copyScenarioSubtree(sql, scope.scenarioIds, includeOutputs)
                }
                if (scope.sysSns.isNotEmpty()) {
                    copySourcesByName(sql, scope.sysSns)
                }
            }
        }
    }

    private fun copyAllScenariosBlock(sql: SupportSQLiteDatabase, includeOutputs: Boolean) {
        COMPONENT_TABLES.forEach { t ->
            sql.execSQL("INSERT INTO export.$t SELECT * FROM $t")
        }
        sql.execSQL("INSERT INTO export.scenarios SELECT * FROM scenarios")
        BRIDGE_TABLES.forEach { b ->
            sql.execSQL("INSERT INTO export.${b.bridge} SELECT * FROM ${b.bridge}")
        }
        if (includeOutputs) {
            OUTPUT_TABLES.forEach { t ->
                sql.execSQL("INSERT INTO export.$t SELECT * FROM $t")
            }
        }
    }

    private fun copyAllSources(sql: SupportSQLiteDatabase) {
        SOURCE_TABLES.forEach { t ->
            sql.execSQL("INSERT INTO export.$t SELECT * FROM $t")
        }
    }

    /**
     * Copy the closure of rows reachable from the selected scenarios:
     *
     *  1. scenarios — directly by `scenarioIndex IN (...)`.
     *  2. bridge rows — by `scenarioID IN (...)`.
     *  3. components — by `<componentIndex> IN (SELECT <componentID> FROM bridge ...)`,
     *     using INSERT OR IGNORE so components shared between two selected
     *     scenarios land exactly once.
     *  4. (optional) outputs scoped to the selected scenarios and components.
     */
    private fun copyScenarioSubtree(
        sql: SupportSQLiteDatabase,
        scenarioIds: Set<Long>,
        includeOutputs: Boolean
    ) {
        val ids = scenarioIds.joinToString(",")

        sql.execSQL("INSERT INTO export.scenarios SELECT * FROM scenarios WHERE scenarioIndex IN ($ids)")

        BRIDGE_TABLES.forEach { b ->
            sql.execSQL(
                "INSERT INTO export.${b.bridge} SELECT * FROM ${b.bridge} " +
                    "WHERE scenarioID IN ($ids)"
            )
            sql.execSQL(
                "INSERT OR IGNORE INTO export.${b.componentTable} " +
                    "SELECT * FROM ${b.componentTable} " +
                    "WHERE ${b.componentPk} IN (" +
                    "  SELECT ${b.componentFk} FROM ${b.bridge} WHERE scenarioID IN ($ids)" +
                    ")"
            )
        }

        if (includeOutputs) {
            sql.execSQL(
                "INSERT INTO export.costings SELECT * FROM costings WHERE scenarioID IN ($ids)"
            )
            sql.execSQL(
                "INSERT INTO export.scenariosimulationdata " +
                    "SELECT * FROM scenariosimulationdata WHERE scenarioID IN ($ids)"
            )
            sql.execSQL(
                "INSERT INTO export.loadprofiledata SELECT * FROM loadprofiledata " +
                    "WHERE loadProfileID IN (" +
                    "  SELECT loadProfileID FROM scenario2loadprofile WHERE scenarioID IN ($ids)" +
                    ")"
            )
            sql.execSQL(
                "INSERT INTO export.paneldata SELECT * FROM paneldata " +
                    "WHERE panelID IN (" +
                    "  SELECT panelID FROM scenario2panel WHERE scenarioID IN ($ids)" +
                    ")"
            )
        }
    }

    private fun copySourcesByName(sql: SupportSQLiteDatabase, sysSns: Set<String>) {
        // sysSn is a string column — quote each value defensively. Production
        // values come from importer code (alphanumeric / MPRN / HA aliases) so
        // collisions are improbable, but the escape pass is cheap.
        val list = sysSns.joinToString(",") { "'${it.replace("'", "''")}'" }
        SOURCE_TABLES.forEach { t ->
            sql.execSQL("INSERT INTO export.$t SELECT * FROM $t WHERE sysSn IN ($list)")
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Schema map — populated from the entity declarations in
    // com.tfcode.comparetout.model.scenario / .importers.alphaess.
    // ────────────────────────────────────────────────────────────────────

    private data class BridgeMap(
        val bridge: String,
        val componentTable: String,
        val componentFk: String,   // column on the bridge row pointing at the component
        val componentPk: String    // PK column on the component table
    )

    private companion object {
        val COMPONENT_TABLES = listOf(
            "inverters",
            "batteries",
            "panels",
            "hwsystem",
            "loadprofile",
            "loadshift",
            "discharge2grid",
            "evcharge",
            "hwschedule",
            "hwdivert",
            "evdivert"
        )

        val BRIDGE_TABLES = listOf(
            BridgeMap("scenario2inverter",     "inverters",      "inverterID",     "inverterIndex"),
            BridgeMap("scenario2battery",      "batteries",      "batteryID",      "batteryIndex"),
            BridgeMap("scenario2panel",        "panels",         "panelID",        "panelIndex"),
            BridgeMap("scenario2hwsystem",     "hwsystem",       "hwSystemID",     "hwSystemIndex"),
            BridgeMap("scenario2loadprofile",  "loadprofile",    "loadProfileID",  "loadProfileIndex"),
            BridgeMap("scenario2loadshift",    "loadshift",      "loadShiftID",    "loadShiftIndex"),
            BridgeMap("scenario2discharge",    "discharge2grid", "dischargeID",    "d2gIndex"),
            BridgeMap("scenario2evcharge",     "evcharge",       "evChargeID",     "evChargeIndex"),
            BridgeMap("scenario2hwschedule",   "hwschedule",     "hwScheduleID",   "hwScheduleIndex"),
            BridgeMap("scenario2hwdivert",     "hwdivert",       "hwDivertID",     "hwDivertIndex"),
            BridgeMap("scenario2evdivert",     "evdivert",       "evDivertID",     "evDivertIndex")
        )

        val OUTPUT_TABLES = listOf(
            "costings",
            "scenariosimulationdata",
            "loadprofiledata",
            "paneldata"
        )

        val SOURCE_TABLES = listOf(
            "alphaESSRawEnergy",
            "alphaESSRawPower",
            "alphaESSTransformedData",
            "alphaESSTransformMeta"
        )
    }
}
