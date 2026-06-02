/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.tfcode.comparetout.model

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileNotFoundException

/**
 * Counterpart to [SnapshotExporter] — opens a user-picked SQLite file as a
 * Room [ToutcDB], runs a structured set of referential-closure checks, and
 * (on commit) merges its rows into the production database.
 *
 * Lives in `com.tfcode.comparetout.model` for the same reason as the
 * exporter — it needs the package-private [ToutcDB.getDatabase] handle and
 * the schema-identity hash that Room enforces on open. No edits to any
 * existing DAO, repository, or Java model class.
 *
 * **Outputs note.** `paneldata` and `loadprofiledata` ARE carried across —
 * paneldata is the per-minute PV generation fetched from PVGIS and the
 * loadprofile expansion is a non-trivial computation; both belong with the
 * inputs from the user's point of view. After [addNewScenarioWithComponents]
 * assigns new autoincrement panelIndex / loadProfileIndex values, the
 * importer queries the freshly inserted scenario, builds an old → new ID
 * map, and copies the staging file's `paneldata` / `loadprofiledata` rows
 * with the FK columns rewritten via raw SQL.
 *
 * `costings` and `scenariosimulationdata` are still regenerated from the
 * imported inputs — they're cheap local computations and re-keying them
 * would also require a pricePlanIndex map for `costings`.
 */
class SnapshotImporter(private val application: Application) {

    /** Staged copy of the user-picked file in the app cache. */
    data class Staged(val file: File) {
        fun delete() { runCatching { file.delete() } }
    }

    /** Result of opening + validating a staged file. */
    sealed class Validation {
        /** Schema mismatch, corrupt file, or empty file — single message for the dialog. */
        data class FileError(val message: String) : Validation()

        /** Schema matched and Room could open the DB. May still carry per-table errors. */
        data class Opened(
            val staged: Staged,
            val summary: Summary,
            val errors: List<String>
        ) : Validation() {
            val ok: Boolean get() = errors.isEmpty()
        }
    }

    /** Counts shown on the preview dialog. */
    data class Summary(
        val plans: Int,
        val scenarios: Int,
        val sources: Int,
        val transformedRows: Long,
        val rawPowerRows: Long,
        val rawEnergyRows: Long,
        val sampleScenarioNames: List<String>,
        val sampleSysSns: List<String>
    )

    /** What changed on commit — used to build the success snackbar. */
    data class CommitResult(
        val plansAdded: Int,
        val plansReplaced: Int,
        val plansSkipped: Int,
        val scenariosAdded: Int,
        val scenariosReplaced: Int,
        val scenariosSkipped: Int,
        val sourcesTouched: Int,
        val warnings: List<String>
    )

    // ── Step 1 — copy picked URI to a real file in cacheDir ─────────────────

    /**
     * Stream the picked content into `cacheDir/import-staging.db`. Returns
     * the staged file or throws if the URI cannot be read. Always overwrites
     * any previous staging file.
     */
    fun stage(uri: Uri): Staged {
        val dir = File(application.cacheDir, "imports").apply { mkdirs() }
        val out = File(dir, "import-staging.db")
        if (out.exists() && !out.delete()) {
            throw IllegalStateException("Could not clear previous staging file")
        }
        val resolver = application.contentResolver
        val input = resolver.openInputStream(uri)
            ?: throw FileNotFoundException("Could not open snapshot URI: $uri")
        input.use { src ->
            out.outputStream().use { dst -> src.copyTo(dst) }
        }
        if (out.length() == 0L) throw IllegalStateException("Snapshot file is empty")
        return Staged(out)
    }

    // ── Step 2 — open as Room (validates schema), then run integrity checks ─

    /**
     * Try to open [staged] as a [ToutcDB]. Room will throw if the schema
     * identity hash does not match the current build, which is exactly the
     * strong gate we want. Returns a [Validation.FileError] for any open-
     * time failure, or [Validation.Opened] (possibly with per-table errors)
     * for a file Room accepted.
     */
    fun validate(staged: Staged): Validation {
        val handle = runCatching {
            val builder = Room.databaseBuilder(application, ToutcDB::class.java, staged.file.absolutePath)
            builder.build()
        }.getOrElse { e ->
            return Validation.FileError(
                "This file is not a Eco Power Optimiser snapshot, or it's from a different app version (${e.message ?: "open failed"})."
            )
        }

        val sql = runCatching { handle.openHelper.readableDatabase }
            .getOrElse { e ->
                handle.close()
                return Validation.FileError(
                    "Could not read the snapshot file (${e.message ?: "open failed"})."
                )
            }

        // Schema is good; gather per-row checks. We accumulate every failure
        // into the `errors` list and let the caller decide how to render.
        return try {
            val summary = summarise(sql)
            if (summary.isEmpty()) {
                Validation.FileError("Snapshot is empty — nothing to import.")
            } else {
                val errors = runChecks(sql)
                Validation.Opened(staged, summary, errors)
            }
        } finally {
            handle.close()
        }
    }

    private fun Summary.isEmpty(): Boolean =
        plans == 0 && scenarios == 0 && transformedRows == 0L && rawEnergyRows == 0L

    private fun summarise(sql: SupportSQLiteDatabase): Summary {
        val plans = countRows(sql, "PricePlans")
        val scenarios = countRows(sql, "scenarios")
        val transformed = countRows(sql, "alphaESSTransformedData")
        val rawPower = countRows(sql, "alphaESSRawPower")
        val rawEnergy = countRows(sql, "alphaESSRawEnergy")

        val scenarioNames = queryStrings(
            sql, "SELECT scenarioName FROM scenarios ORDER BY scenarioName LIMIT 5"
        )
        val sysSns = queryStrings(
            sql,
            // Union the two source families so the preview lists every distinct
            // sysSn even if only one table has rows for it.
            "SELECT sysSn FROM (" +
                " SELECT DISTINCT sysSn FROM alphaESSRawEnergy" +
                " UNION SELECT DISTINCT sysSn FROM alphaESSTransformedData" +
                ") ORDER BY sysSn LIMIT 5"
        )
        val sourceCount = countRows(sql, "(SELECT DISTINCT sysSn FROM alphaESSRawEnergy " +
            "UNION SELECT DISTINCT sysSn FROM alphaESSTransformedData)")

        return Summary(
            plans = plans.toInt(),
            scenarios = scenarios.toInt(),
            sources = sourceCount.toInt(),
            transformedRows = transformed,
            rawPowerRows = rawPower,
            rawEnergyRows = rawEnergy,
            sampleScenarioNames = scenarioNames,
            sampleSysSns = sysSns
        )
    }

    private fun runChecks(sql: SupportSQLiteDatabase): List<String> {
        val errors = mutableListOf<String>()

        // 1. Plans referential closure.
        val orphanedDayRates = countRows(
            sql,
            "DayRates",
            "WHERE pricePlanId NOT IN (SELECT pricePlanIndex FROM PricePlans)"
        )
        if (orphanedDayRates > 0) {
            errors += "$orphanedDayRates day rates reference a plan that isn't in the file."
        }

        // 2. Scenario name uniqueness inside the file.
        val dupeScenarioNames = queryStrings(
            sql,
            "SELECT scenarioName FROM scenarios GROUP BY scenarioName HAVING COUNT(*) > 1 LIMIT 5"
        )
        if (dupeScenarioNames.isNotEmpty()) {
            errors += "Duplicate scenario names in the file: ${dupeScenarioNames.joinToString(", ")}."
        }

        // 3. Bridge / component referential closure.
        BRIDGE_CHECKS.forEach { b ->
            val missingScenario = countRows(
                sql,
                b.bridge,
                "WHERE scenarioID NOT IN (SELECT scenarioIndex FROM scenarios)"
            )
            if (missingScenario > 0) {
                errors += "$missingScenario ${b.bridge} rows reference a missing scenario."
            }
            val missingComponent = countRows(
                sql,
                b.bridge,
                "WHERE ${b.componentFk} NOT IN (SELECT ${b.componentPk} FROM ${b.componentTable})"
            )
            if (missingComponent > 0) {
                errors += "$missingComponent ${b.bridge} rows reference a missing ${b.componentTable} row."
            }
        }

        // 4. Output closure (these tables are not imported, but we still flag
        //    that the file is internally inconsistent — useful diagnostic).
        listOf(
            Triple("costings", "scenarioID", "scenarios.scenarioIndex"),
            Triple("scenariosimulationdata", "scenarioID", "scenarios.scenarioIndex"),
            Triple("paneldata", "panelID", "panels.panelIndex"),
            Triple("loadprofiledata", "loadProfileID", "loadprofile.loadProfileIndex")
        ).forEach { (table, fkCol, parent) ->
            val (parentTable, parentCol) = parent.split(".")
            val orphans = countRows(
                sql,
                table,
                "WHERE $fkCol NOT IN (SELECT $parentCol FROM $parentTable)"
            )
            if (orphans > 0) {
                errors += "$orphans $table rows reference a missing $parentTable row."
            }
        }

        // 5. Source rows — sysSn must be present.
        listOf("alphaESSRawEnergy", "alphaESSRawPower", "alphaESSTransformedData").forEach { t ->
            val blanks = countRows(sql, t, "WHERE sysSn IS NULL OR sysSn = ''")
            if (blanks > 0) errors += "$blanks $t rows have a blank sysSn."
        }

        return errors
    }

    // ── Step 3 — apply the import inside one transaction on the live DB ─────

    /**
     * Merge the staged snapshot into the production database. Plans and
     * scenarios go through the existing repository entry points (they
     * already re-key autoincrement PKs and handle clobber-by-name). Sources
     * are copied via ATTACH because their PK is the natural [sysSn].
     *
     * @param staged file that has already passed [validate].
     * @param replaceExisting when true, production rows whose natural key
     *   (planName for plans, scenarioName for scenarios, sysSn for sources)
     *   matches an imported row are deleted before the imported row is
     *   inserted. When false, the imported row is skipped.
     */
    fun commit(staged: Staged, replaceExisting: Boolean): CommitResult {
        val warnings = mutableListOf<String>()

        // Phase 1 — drain the staging file into in-memory objects via a
        // throw-away Room handle. Closing here releases the file so the
        // live DB can ATTACH to it for the source-row copy below.
        val staging = Room.databaseBuilder(application, ToutcDB::class.java, staged.file.absolutePath)
            .build()
        val stagingPlans: Map<com.tfcode.comparetout.model.priceplan.PricePlan,
            List<com.tfcode.comparetout.model.priceplan.DayRate>>?
        val stagingScenarios: List<com.tfcode.comparetout.model.scenario.ScenarioComponents>?
        val stagingSysSns: Set<String>
        try {
            stagingPlans = staging.pricePlanDAO().allPricePlansForExport
            stagingScenarios = staging.scenarioDAO().allScenariosForExport
            stagingSysSns = queryStrings(
                staging.openHelper.readableDatabase,
                "SELECT DISTINCT sysSn FROM (" +
                    " SELECT sysSn FROM alphaESSRawEnergy" +
                    " UNION SELECT sysSn FROM alphaESSRawPower" +
                    " UNION SELECT sysSn FROM alphaESSTransformedData" +
                    " UNION SELECT sysSn FROM alphaESSTransformMeta" +
                    ")"
            ).toHashSet()
        } finally {
            staging.close()
        }

        // Phase 2 — plans and scenarios via the existing DAO entry points
        // (they re-key autoincrement PKs and respect the clobber flag).
        val plansResult = applyPlans(stagingPlans, replaceExisting)
        val scenariosResult = applyScenarios(stagingScenarios, replaceExisting)

        // Phase 3 — paneldata, loadprofiledata, and source tables all in a
        // single ATTACH session. paneldata / loadprofiledata FK columns are
        // rewritten using the maps built in applyScenarios so the rows land
        // against the freshly-assigned panelIndex / loadProfileIndex.
        val sourcesTouched = applyAttachedTables(
            staged,
            scenariosResult.panelIdMap,
            scenariosResult.loadProfileIdMap,
            stagingSysSns,
            replaceExisting,
            warnings
        )

        // Discard the staging file once everything has been committed.
        staged.delete()

        return CommitResult(
            plansAdded = plansResult.added,
            plansReplaced = plansResult.replaced,
            plansSkipped = plansResult.skipped,
            scenariosAdded = scenariosResult.added,
            scenariosReplaced = scenariosResult.replaced,
            scenariosSkipped = scenariosResult.skipped,
            sourcesTouched = sourcesTouched,
            warnings = warnings
        )
    }

    private data class PlansApply(val added: Int, val replaced: Int, val skipped: Int)

    /** Counts of how each imported scenario was handled, plus the
     *  panel- and load-profile-ID translations we need to copy paneldata
     *  and loadprofiledata across with their FK columns rewritten. */
    private data class ScenariosApply(
        val added: Int,
        val replaced: Int,
        val skipped: Int,
        val panelIdMap: Map<Long, Long>,
        val loadProfileIdMap: Map<Long, Long>
    )

    private fun applyPlans(
        plans: Map<com.tfcode.comparetout.model.priceplan.PricePlan,
            List<com.tfcode.comparetout.model.priceplan.DayRate>>?,
        replaceExisting: Boolean
    ): PlansApply {
        if (plans.isNullOrEmpty()) return PlansApply(0, 0, 0)

        val live = ToutcDB.getDatabase(application)
        val plansDAO = live.pricePlanDAO()
        // Snapshot existing names so we can classify each imported plan.
        val existingNames = plansDAO.loadPricePlansNow()?.map { it.planName }?.toHashSet().orEmpty()

        var added = 0
        var replaced = 0
        var skipped = 0
        plans.forEach { (importedPlan, importedDayRates) ->
            val collides = importedPlan.planName in existingNames
            if (collides && !replaceExisting) {
                skipped += 1
                return@forEach
            }
            // Reset autoincrement PKs so Room re-assigns them on insert.
            importedPlan.pricePlanIndex = 0
            importedDayRates.forEach { it.dayRateIndex = 0 }
            plansDAO.addNewPricePlanWithDayRates(importedPlan, importedDayRates, replaceExisting)
            if (collides) replaced += 1 else added += 1
        }
        return PlansApply(added, replaced, skipped)
    }

    private fun applyScenarios(
        scenarios: List<com.tfcode.comparetout.model.scenario.ScenarioComponents>?,
        replaceExisting: Boolean
    ): ScenariosApply {
        if (scenarios.isNullOrEmpty()) {
            return ScenariosApply(0, 0, 0, emptyMap(), emptyMap())
        }

        val live = ToutcDB.getDatabase(application)
        val scenarioDAO = live.scenarioDAO()
        // Synchronous read of current names — the LiveData path requires an
        // observer and would race against this commit.
        val existingNames = scenarioDAO.allScenariosForExport
            ?.map { it.scenario.scenarioName }?.toHashSet().orEmpty()

        var added = 0
        var replaced = 0
        var skipped = 0
        val panelIdMap = HashMap<Long, Long>()
        val loadProfileIdMap = HashMap<Long, Long>()

        scenarios.forEach { sc ->
            val collides = sc.scenario.scenarioName in existingNames
            if (collides && !replaceExisting) {
                skipped += 1
                return@forEach
            }

            // Capture the staging PKs we'll need to translate before
            // zeroScenarioPks wipes them. Panels are kept in list order so
            // we can pair them with the post-insert query result, which is
            // sorted by the new autoincrement panelIndex (= insertion
            // order inside addNewScenarioWithComponents).
            val oldPanelIds = sc.panels?.map { it.panelIndex } ?: emptyList()
            val oldLoadProfileId = sc.loadProfile?.loadProfileIndex ?: 0L

            zeroScenarioPks(sc)
            val newScenarioId = scenarioDAO.addNewScenarioWithComponents(
                sc.scenario, sc, replaceExisting
            )
            if (collides) replaced += 1 else added += 1

            // Build the panel-ID translation. We can't read the assigned
            // indexes off the in-memory Panel objects (the DAO never writes
            // them back), so we query the live DB instead.
            if (oldPanelIds.isNotEmpty()) {
                val newPanels = scenarioDAO.getPanelsForScenarioID(newScenarioId)
                    .sortedBy { it.panelIndex }
                if (newPanels.size == oldPanelIds.size) {
                    oldPanelIds.forEachIndexed { i, oldId ->
                        if (oldId != 0L) panelIdMap[oldId] = newPanels[i].panelIndex
                    }
                }
                // If the sizes don't match, the imported file already had
                // missing/duplicated panel rows for this scenario — the
                // referential checks during validate should have caught it,
                // so we don't try to recover here.
            }

            if (oldLoadProfileId != 0L) {
                val newLp = scenarioDAO.getLoadProfileForScenarioID(newScenarioId)
                if (newLp != null) {
                    loadProfileIdMap[oldLoadProfileId] = newLp.loadProfileIndex
                }
            }
        }
        return ScenariosApply(added, replaced, skipped, panelIdMap, loadProfileIdMap)
    }

    /**
     * Reset every autoincrement primary key on a [ScenarioComponents] tree
     * to 0 so Room assigns fresh IDs at insert time. Without this the
     * imported scenario would try to re-use the source database's IDs and
     * collide with existing production rows.
     */
    private fun zeroScenarioPks(sc: com.tfcode.comparetout.model.scenario.ScenarioComponents) {
        sc.scenario.scenarioIndex = 0
        sc.inverters?.forEach { it.inverterIndex = 0 }
        sc.batteries?.forEach { it.batteryIndex = 0 }
        sc.panels?.forEach { it.panelIndex = 0 }
        sc.hwSystem?.hwSystemIndex = 0
        sc.loadProfile?.loadProfileIndex = 0
        sc.loadShifts?.forEach { it.loadShiftIndex = 0 }
        sc.discharges?.forEach { it.d2gIndex = 0 }
        sc.evCharges?.forEach { it.evChargeIndex = 0 }
        sc.hwSchedules?.forEach { it.hwScheduleIndex = 0 }
        sc.hwDivert?.hwDivertIndex = 0
        sc.evDiverts?.forEach { it.evDivertIndex = 0 }
    }

    /**
     * Bulk-copy rows that share the staging file: paneldata (PV fetched
     * from PVGIS, not regeneratable without internet), loadprofiledata
     * (per-minute load expansion), and the four AlphaESS source tables.
     *
     * paneldata / loadprofiledata FK columns are rewritten on the fly via
     * the [panelIdMap] / [loadProfileIdMap] built in applyScenarios.
     * AlphaESS tables use sysSn as natural key, so they don't need
     * translation — just `INSERT OR REPLACE` (replace) or `INSERT OR
     * IGNORE` (skip).
     *
     * Returns the number of distinct sysSns touched (for the snackbar).
     */
    private fun applyAttachedTables(
        staged: Staged,
        panelIdMap: Map<Long, Long>,
        loadProfileIdMap: Map<Long, Long>,
        sysSns: Set<String>,
        replaceExisting: Boolean,
        warnings: MutableList<String>
    ): Int {
        if (panelIdMap.isEmpty() && loadProfileIdMap.isEmpty() && sysSns.isEmpty()) return 0

        val live = ToutcDB.getDatabase(application)
        val sql = live.openHelper.writableDatabase
        val attachPath = staged.file.absolutePath.replace("'", "''")
        sql.execSQL("ATTACH DATABASE '$attachPath' AS staging")
        try {
            sql.beginTransaction()
            try {
                copyPanelData(sql, panelIdMap, replaceExisting, warnings)
                copyLoadProfileData(sql, loadProfileIdMap, replaceExisting, warnings)
                if (sysSns.isNotEmpty()) {
                    copySourceTables(sql, sysSns, replaceExisting)
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
        } catch (t: Throwable) {
            warnings += "Attached-table copy failed: ${t.message ?: "unknown error"}"
        } finally {
            sql.execSQL("DETACH DATABASE staging")
        }
        return sysSns.size
    }

    private fun copyPanelData(
        sql: SupportSQLiteDatabase,
        panelIdMap: Map<Long, Long>,
        replaceExisting: Boolean,
        warnings: MutableList<String>
    ) {
        if (panelIdMap.isEmpty()) return
        // paneldata.panelID is part of a composite primary key, so OR REPLACE
        // and OR IGNORE both behave correctly on collisions. The freshly-
        // inserted scenario has no paneldata yet (we wiped it on insert via
        // addNewScenarioWithComponents), so the conflict clause only matters
        // for the rare case where two imported scenarios share a panel.
        val conflictClause = if (replaceExisting) "OR REPLACE" else "OR IGNORE"
        panelIdMap.forEach { (oldId, newId) ->
            try {
                sql.execSQL(
                    "INSERT $conflictClause INTO paneldata " +
                        "(panelID, date, minute, pv, mod, dow, do2001, millisSinceEpoch) " +
                        "SELECT $newId, date, minute, pv, mod, dow, do2001, millisSinceEpoch " +
                        "FROM staging.paneldata WHERE panelID = $oldId"
                )
            } catch (t: Throwable) {
                warnings += "Skipped paneldata for old panel $oldId: ${t.message ?: "error"}"
            }
        }
    }

    private fun copyLoadProfileData(
        sql: SupportSQLiteDatabase,
        loadProfileIdMap: Map<Long, Long>,
        replaceExisting: Boolean,
        warnings: MutableList<String>
    ) {
        if (loadProfileIdMap.isEmpty()) return
        val conflictClause = if (replaceExisting) "OR REPLACE" else "OR IGNORE"
        loadProfileIdMap.forEach { (oldId, newId) ->
            try {
                sql.execSQL(
                    "INSERT $conflictClause INTO loadprofiledata " +
                        "(loadProfileID, date, minute, load, mod, dow, do2001, millisSinceEpoch) " +
                        "SELECT $newId, date, minute, load, mod, dow, do2001, millisSinceEpoch " +
                        "FROM staging.loadprofiledata WHERE loadProfileID = $oldId"
                )
            } catch (t: Throwable) {
                warnings += "Skipped loadprofiledata for old profile $oldId: ${t.message ?: "error"}"
            }
        }
    }

    private fun copySourceTables(
        sql: SupportSQLiteDatabase,
        sysSns: Set<String>,
        replaceExisting: Boolean
    ) {
        if (replaceExisting) {
            val list = sysSns.joinToString(",") { "'${it.replace("'", "''")}'" }
            SOURCE_TABLES.forEach { t ->
                sql.execSQL("DELETE FROM $t WHERE sysSn IN ($list)")
            }
        }
        val conflictClause = if (replaceExisting) "OR REPLACE" else "OR IGNORE"
        SOURCE_TABLES.forEach { t ->
            sql.execSQL("INSERT $conflictClause INTO $t SELECT * FROM staging.$t")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun countRows(sql: SupportSQLiteDatabase, table: String, where: String? = null): Long {
        val q = if (where == null) "SELECT COUNT(*) FROM $table"
                else "SELECT COUNT(*) FROM $table $where"
        return sql.query(q).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    }

    private fun queryStrings(sql: SupportSQLiteDatabase, q: String): List<String> {
        val out = mutableListOf<String>()
        sql.query(q).use { c ->
            while (c.moveToNext()) {
                if (!c.isNull(0)) out += c.getString(0)
            }
        }
        return out
    }

    private data class BridgeCheck(
        val bridge: String,
        val componentFk: String,
        val componentTable: String,
        val componentPk: String
    )

    private companion object {
        val BRIDGE_CHECKS = listOf(
            BridgeCheck("scenario2inverter",    "inverterID",    "inverters",      "inverterIndex"),
            BridgeCheck("scenario2battery",     "batteryID",     "batteries",      "batteryIndex"),
            BridgeCheck("scenario2panel",       "panelID",       "panels",         "panelIndex"),
            BridgeCheck("scenario2hwsystem",    "hwSystemID",    "hwsystem",       "hwSystemIndex"),
            BridgeCheck("scenario2loadprofile", "loadProfileID", "loadprofile",    "loadProfileIndex"),
            BridgeCheck("scenario2loadshift",   "loadShiftID",   "loadshift",      "loadShiftIndex"),
            BridgeCheck("scenario2discharge",   "dischargeID",   "discharge2grid", "d2gIndex"),
            BridgeCheck("scenario2evcharge",    "evChargeID",    "evcharge",       "evChargeIndex"),
            BridgeCheck("scenario2hwschedule",  "hwScheduleID",  "hwschedule",     "hwScheduleIndex"),
            BridgeCheck("scenario2hwdivert",    "hwDivertID",    "hwdivert",       "hwDivertIndex"),
            BridgeCheck("scenario2evdivert",    "evDivertID",    "evdivert",       "evDivertIndex")
        )

        val SOURCE_TABLES = listOf(
            "alphaESSRawEnergy",
            "alphaESSRawPower",
            "alphaESSTransformedData",
            "alphaESSTransformMeta"
        )
    }
}

/**
 * Convenience wrapper for callers that just want "pick a URI, validate,
 * commit if OK". Not used by the activity directly — kept here so it's easy
 * to drive the importer from a unit test or a future scripting hook.
 */
@Suppress("unused")
internal fun runSnapshotImport(
    context: Context,
    uri: Uri,
    replaceExisting: Boolean
): SnapshotImporter.CommitResult? {
    val importer = SnapshotImporter(context.applicationContext as Application)
    val staged = importer.stage(uri)
    return when (val v = importer.validate(staged)) {
        is SnapshotImporter.Validation.FileError -> { staged.delete(); null }
        is SnapshotImporter.Validation.Opened -> {
            if (v.ok) importer.commit(staged, replaceExisting) else { staged.delete(); null }
        }
    }
}
