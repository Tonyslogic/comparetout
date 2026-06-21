# Adding a Simulation Component

How to add a new component to the energy-simulation pipeline — from the pure engine all the way out to the
DB schema, JSON round-trip, wizard, dashboard, graphs, comparison, and import/export.

This is the deliverable promised by `plans/sim/component.md` (Phase E). It uses the **existing** components
as worked examples (EV charge as a demand contributor, EV/HW divert as surplus sinks, the inverter as the
Converter/Storage/Generator bundle). The **heat pump (HP)** is the next intended user — it is *not* built
here, but each section flags what the HP would touch, so you can use this as the map when you implement it.

> Product note: the user-facing name is **Eco Power Optimiser**; `comparetout` / `TOUTC` are internal
> codenames only.

---

## 0. Two kinds of component — pick your path

| | **Pure (engine-only)** | **Persisted (user-configurable)** |
|---|---|---|
| Lives only in the per-interval solve | ✅ | ✅ |
| User can configure it, it survives a restart, it round-trips through JSON/snapshot | ❌ | ✅ |
| Touches DB schema, DAO, JSON, wizard, graphs | ❌ | ✅ |
| Work | **Section 1** (≈ one interface + one registry line) | **Sections 2–13** (the full checklist) |

A heat pump is a **persisted** component (the user enters its fuel use, COP, capacity, schedule…), so it
walks the whole checklist. Read Section 1 first regardless — it is the heart, and the rest is plumbing that
carries the user's configuration to it.

The guiding principle of the component refactor (Beck): *make the change easy, then make the easy change.*
The engine seam (Section 1) is the "easy change"; Sections 2–13 are the plumbing that already exists for
every other component, so adding yours is **following a pattern, not inventing one**. When in doubt, grep for
how `EVCharge`/`EVDivert` or `Battery` does the same step and mirror it.

---

## 1. The engine seam (the only part that is really "simulation")

All pure abstractions live in `app/src/main/java/com/tfcode/comparetout/scenario/sim/` (no Android, no Room —
unit-testable with plain JUnit4). The per-interval solve is `SimulationEngine.processOneRow`; it never
hardcodes a component, it **consults the registry** for each capability.

### 1.1 Capabilities — implement the one(s) you need

| Capability (`scenario.sim`) | Pipeline stage | Implement it if your component… |
|---|---|---|
| `DemandContributor` | scheduled electrical demand, **before** the energy flow | …draws electricity on a schedule (HW immersion, EV charge). **HP electrical demand = thermal ÷ COP** is this. |
| `SurplusSink` | consumes surplus PV (`feed`), **after** the flow | …soaks up excess PV (HW divert, EV divert). An HP "dump surplus into the thermal store" would be this. |
| `Generator` | puts energy on an inverter's DC bus | …generates (PV). |
| `Storage` | stores/releases energy (SOC, charge/discharge capacity) | …is a battery. |
| `Converter` | AC throughput cap + DC/AC/DC-DC losses + dispatch strategy | …is an inverter. |
| `GridExchange` | scheduled grid charge (CFG) / forced discharge (FD2G) | …schedules grid import/export. |

The bundle `InverterComponent = Generator + Storage + Converter + GridExchange` is how an inverter exposes all
four; the coupled bus physics stays in the engine (it asks each component for its capability *values* and runs
the shared-AC-bus solve itself — a component never sees another's bus internals).

`DemandContributor` and `SurplusSink` are the seams a new load-like component uses:

```java
public interface DemandContributor {
    DemandResult demand(IntervalContext ctx);   // DemandResult{ double kWh; Map<OutputChannel,Double> outputs }
}
public interface SurplusSink {
    double absorb(double availableKWh, IntervalContext ctx);  // returns kWh actually absorbed
    OutputChannel divertChannel();                            // which output column its absorption lands in
}
```

`IntervalContext` is read-only and resolved once per interval: `millis, month, dayOfWeek, minuteOfDay,
evDivertDay, intervalHours (=1/12)`. Components see demand/surplus and time — **never** the bus state.

**Worked example — `HwComponent`** (`scenario.sim/HwComponent.java`) implements **both**: as a
`DemandContributor` it returns the scheduled immersion kWh (and routes it to `OutputChannel.IMMERSION_LOAD`);
as a `SurplusSink` it absorbs surplus PV into the tank (routing to `DIV_TO_WATER`) and owns the water
temperature across intervals. **Worked example — `EvChargeComponent`** is demand-only; `EvDivertComponent` is
a sink-only with a per-day cap. Mirror whichever shape matches your component.

### 1.2 Cross-interval state lives **inside** the component

Components are instantiated **once per scenario run** and may hold their own state (HW owns `waterTemp`; an HP
would own thermal-store / building temperature). The engine no longer reads the previous output row for state.
Two lifecycle hooks:

- The engine calls a reset on the **first** interval (`outputRows.isEmpty()`), reproducing "no prior row ⇒
  start state". `HwComponent.resetWaterTemp()` is the example. Implement the equivalent so a re-run / reused
  scenario is deterministic.
- For state you commit each interval (like water temp), expose a `commit…()` the engine calls after the divert
  pass and route it to an output channel.

### 1.3 Output routing — no engine knowledge of your columns

The engine must not call `setHeatPumpLoad(...)` directly. Instead your component returns `(OutputChannel,
value)` pairs and the engine's `applyOutputs` maps each channel to the right `ScenarioSimulationData` setter.
So adding a component's new output is: **add an enum value + one `case` in `applyOutputs`** — never an edit to
the per-interval loop.

`scenario.sim/OutputChannel.java`:
```java
public enum OutputChannel {
    DIRECT_EV_CHARGE,   // -> setDirectEVcharge
    IMMERSION_LOAD,     // -> setImmersionLoad
    DIV_TO_WATER,       // -> setKWHDivToWater
    DIV_TO_EV,          // -> setKWHDivToEV
    WATER_TEMP          // -> setWaterTemp
    // HEAT_PUMP_LOAD,  // -> setHeatPumpLoad   (add when you add the column — Section 7)
}
```
`SimulationEngine.applyOutputs(...)` is the single switch that routes them. Add your `case` there.

### 1.4 Register it — `ComponentRegistry.build(...)`

`scenario.sim/ComponentRegistry.java` is **the factory and the extension point**. `build(...)` is the single
place that maps scenario entities → components ("scenario has HW ⇒ register HW demand + HW divert sink",
"…has EV ⇒ …"). `ScenarioInputs` *holds* the registry (built once per scenario in its constructor).

Adding a component to the engine is, in total:
1. implement the capability interface(s) in `scenario.sim`,
2. add it to the right list inside `ComponentRegistry.build(...)` (and to `demandContributors()` /
   `divertOrder()` as appropriate),
3. add an `OutputChannel` + `applyOutputs` case if it writes a new column.

If your component does **not** need to be configured/persisted, you are done — wire `build(...)` to construct
it from a constant or an existing input. Everything below carries *user configuration* to `build(...)`.

> **Divert ordering** is a *strategy*, the analogue of `DispatchStrategy` for batteries. The order in which
> sinks consume `feed` is decided by `ComponentRegistry.divertOrder(ctx)` (driven by `EVDivert.isEv1st()`),
> and the engine makes a **single ordered pass** (each sink absorbs at most once). If your component is a
> sink, decide where it sits in that order and pin it with a test — this is the highest-risk
> behaviour-preserving area (it's where the legacy "double-heat" bug lived).

---

## 2. Domain entity + DB schema (persisted only)

> **Hard rule:** DB schema changes require explicit approval, and you must **not** edit `ScenarioDAO.java`,
> `ToutcRepository.java`, or the Room `@Entity` model classes without it. The millis columns already exist —
> no migration is needed for time. Everything in Sections 2–4 is schema/DAO work; get sign-off first.

1. **Entity** `model/scenario/HeatPump.java` — a Room `@Entity(tableName = "heatpumps")` with an
   `@PrimaryKey(autoGenerate = true)` id and your config fields (fuel-use-derived annual kWh, COP, capacity,
   comfort band, an inverter/owner name if it attaches to one, etc.). Mirror `Battery.java` / `HWSystem.java`.
2. **Junction** `model/scenario/Scenario2HeatPump.java` — `@Entity(tableName = "scenario2heatpump")` with
   `s2hpID` (autogen PK), `heatPumpID`, `scenarioID`. Copy `Scenario2Battery.java` verbatim and rename.
3. **Capability flag on `Scenario`** — add `isHasHeatPump` (mirrors `isHasBatteries`, `isHasHWSystem`). The UI
   uses these flags to avoid JOINs.
4. **Register both entities** in `model/ToutcDB.java`'s `@Database(entities = {…})` and **bump the version**
   `8 → 9`, adding `@AutoMigration(from = 8, to = 9)` to the chain. Room generates the migration automatically
   for *new tables* and for *added columns that have a default* — so give any new non-null column a
   `defaultValue` (the `dispatchMode` v7→v8 add is the precedent; the nullable `millisSinceEpoch` columns are
   another). A column without a usable default needs a hand-written migration.

> Note on `scenarios.scenarioName`: it has a **UNIQUE index**. This bites the save path (Section 9) — keep it
> in mind, it is the cause of the "name already in use" handling.

---

## 3. DAO (persisted only — gated)

All in `model/ScenarioDAO.java`. For each existing component there is a consistent set; replicate it. Using
battery as the template:

- **Read:** `getHeatPumpsForScenarioID(id)` — a JOIN through `scenario2heatpump` (copy
  `getBatteriesForScenarioID`, lines ~377).
- **Inserts:** `@Insert abstract long addNewHeatPump(...)`, `@Insert abstract void
  addNewScenario2HeatPump(...)` (lines ~117, ~150).
- **Wire into `addNewScenarioWithComponents`** (the bulk insert used by NEW/COPY/import, lines ~243–342):
  set the `isHasHeatPump` flag, then a block that inserts each heat pump + its junction (copy the battery
  block at ~253–261). **Gotcha:** this method catches `SQLiteConstraintException` (a duplicate
  `scenarioName`) and now returns `0` as a clear "not created" sentinel — don't reintroduce a bogus id.
- **Per-scenario save helper** `saveHeatPumpForScenario(scenarioID, hp)` (copy `saveBatteryForScenario`,
  ~1046): insert + junction on `id == 0`, else `updateHeatPump`. **Null-guard `getScenario(scenarioID)`**
  before `setHasHeatPump` (a bad id must not NPE — see the battery/panel guards).
- **Delete:** `deleteHeatPumpFromScenario(hpID, scenarioID)` (deletes the *junction* only), and an orphan
  query `@Query("DELETE FROM heatpumps WHERE heatPumpIndex NOT IN (SELECT heatPumpID FROM
  scenario2heatpump)") deleteOrphanHeatPumps()`.
- **Add to the cleanups:** `deleteScenario(...)` runs every `deleteOrphan*` — add yours (lines ~600–610), and
  add a `deleteHeatPumpRelationsForScenario` to its junction-cleanup block.
- **Copy / link:** `copyHeatPumpFromScenario` / `linkHeatPumpFromScenario` (mirror the battery versions,
  ~1073/1093) — used by the wizard's COPY/LINK modes.

> **Edit-save junction gotcha (cost us a real bug):** the wizard edit path *deletes-and-reinserts* a
> scenario's components. The delete removes the **junction**; the re-insert only re-creates the junction on
> the `id == 0` (insert) branch. So when the wizard re-adds, it must pass `id = 0` (see Section 9) or the
> component is left **orphaned** (row exists, link gone) and silently vanishes from the scenario. Batteries
> hit exactly this. Build your `toHeatPump()` / save path so the re-insert always re-creates the junction.

### `ScenarioComponents`

Add `public List<HeatPump> heatPumps;` to `model/scenario/ScenarioComponents.java` and to its constructor.
This bundle is the currency between the DAO, JsonTools, and the wizard — almost every other layer consumes it.

---

## 4. Repository passthroughs (persisted only — gated)

`model/ToutcRepository.java` exposes the DAO to the rest of the app (the wizard/activities can't reach the DAO
directly). Add thin passthroughs for each helper you'll call from the UI: `saveHeatPumpForScenario`,
`deleteHeatPumpFromScenario`, the copy/link ones, and — importantly — include your orphan cleanup in
`deleteOrphanComponents()` (the wizard calls this once per save to prune the orphans the edit path creates).

---

## 5. JSON round-trip (persisted only)

This is the **per-scenario share/import** format (the Share button on a scenario, and "paste JSON" in the
wizard and Import/Export). Snapshot/DB import is separate (Section 10).

1. `model/json/scenario/HeatPumpJson.java` — POJO with `@SerializedName("…")` for each field. **The
   serialized names are the public contract** (e.g. battery uses `"Battery Size"`, `"Discharge stop"`). Mirror
   `BatteryJson.java`.
2. `model/json/scenario/ScenarioJsonFile.java` — add `@SerializedName("HeatPumps") ArrayList<HeatPumpJson>
   heatPumps;` (mirror the `Batteries` field).
3. `model/json/JsonTools.java` — add both directions:
   - `createHeatPump(HeatPumpJson)` and `createHeatPumpList(...)` (entity ← JSON), used by
     `createScenarioComponentList(...)` (add a `createHeatPumpList(sjf.heatPumps)` argument there, ~893).
   - `createHeatPumpListJson(List<HeatPump>)` (JSON ← entity), used by `createSingleScenarioJson(...)` so the
     Share/export path includes it.

> **Empty-object gotcha:** a partial JSON like `"HeatPump": {}` must **not** materialise a fully-defaulted
> component. `createHWSystem` learned this the hard way — guard on a required field (`if (hwj == null ||
> hwj.hwCapacity == null) return null;`) instead of letting a null-field setter NPE into a default object.

A `JsonTools` round-trip unit test (parse → `createScenarioComponentList` → assert your component survives) is
cheap insurance; `ScenarioImportParseTest` is the template.

---

## 6. Connect the user's config to the engine

This is the bridge from Sections 2–5 (persistence) to Section 1 (engine):

- `SimulationWorker.doWork()` (the adapter, ~134–255) reads `ScenarioComponents` and builds `ScenarioInputs`.
  Pass your heat-pump list into the `ScenarioInputs` constructor.
- `ScenarioInputs` builds the `ComponentRegistry` — extend `ComponentRegistry.build(...)` to construct your
  `HeatPumpComponent` from the config and register it (Section 1.4). This is the "one line" the seam promised.
- If your component needs an external input series (the HP needs **outdoor temperature**), treat it like PV:
  a millis-keyed series merged onto the grid at ingestion, or a monthly/seasonal profile. That is its own
  ingestion path (a dependency to plan separately).

---

## 7. Output column + graph/compare/dashboard metrics

If your component produces a number worth showing (an HP's electrical load, or a thermal-store temperature):

1. **Column** on `model/scenario/ScenarioSimulationData.java` — add `heatPumpLoad` (give it a `defaultValue`
   so the AutoMigration from Section 2 is automatic). This entity has a *fixed* column set today; immersion
   and EV each got a dedicated column — yours mirrors that.
2. **Populate it** from the engine via the `OutputChannel` you added in Section 1.3 — no loop edit.
3. **Aggregations** in `ScenarioDAO`:
   - **KPI totals (gotcha):** the headline query sums *component loads into the load total* —
     `SUM(load) + SUM(immersionLoad) + SUM(directEVcharge) AS load` (line ~852). **Add `+ SUM(heatPumpLoad)`**
     or your component's consumption won't count in the KPI/cost "load". This is the single easiest thing to
     forget.
   - **Bar charts:** `getBarData` / `getMonthlyBarData` / `getYearBarData` build `ScenarioBarChartData` from
     `SUM(...)` of the flow columns. Add your column and a field on `ScenarioBarChartData`.
   - **Line graphs (state metrics):** `getLineData` selects `minuteOfDay, SOC, waterTemp` into
     `ScenarioLineGraphData`. A thermal-state metric goes here alongside `waterTemp`.

   > **Two aggregation *kinds* (gotcha):** energies are **summed** per bucket (`SUM(heatPumpLoad)`), but
   > *driver/state* metrics — COP, outdoor temp, wind speed — must be **averaged** (`AVG(heatPumpCop)`), not
   > summed, or a daily bucket reports "24× the COP". The HP added three of each. The averaged queries have an
   > **inner-`SUM` / outer-`AVG`** shape (the interval aggregation sums to a row, the windowing layer averages
   > the rows) — add `avg(driver)` to the inner select **and** `avg(DRIVER)` to the outer. Sums only need the
   > inner `SUM`.

4. **The graph read path has TWO sources — wire BOTH or you get "all zeros" (the bug that cost us most):**
   the UI2 graph (`ui2/UI2GraphsFragment.kt` + `UI2GraphsViewModel.kt`) builds its `ChartPoint`s from one of
   two row types depending on the view:
   - **Single-day view** → `ScenarioBarChartData` (via `getBarData`). Add your field here (step 3).
   - **Windowed / multi-interval view (the default)** → `IntervalRow` (via the `sumHour`/`avgHour`/`…DOY`/
     `…DOW`/`…Month`/`…Year` queries). **`IntervalRow` is in the `model.importers` package and is SHARED by
     importer aggregation queries**, so adding a field there has two consequences:
     1. Add the field + `@ColumnInfo` to `IntervalRow` and `SUM`/`AVG` it in **all ten** scenario interval
        queries in `ScenarioDAO`.
     2. **Every importer interval query (`AlphaEssDAO`, etc.) must also emit a placeholder** — `0 AS HEATPUMP,
        0 AS HEATPUMPCOP, …` — for the new columns. Room fails at compile with *"columns returned by the query
        do not have fields [heatPump…] though they are annotated non-null or primitive"* if any query that maps
        to `IntervalRow` omits them. (There were ~10 importer queries to patch.)

   If you populate `ScenarioBarChartData` but forget `IntervalRow`, the *single-day* view shows your data and
   the *default windowed* view shows zeros — which looks like "the sim didn't run". It did; confirm with a
   diagnostic `Log` of the written totals before chasing the engine.

5. **Filter wiring is THREE places** (`UI2GraphsViewModel.kt` + `UI2GraphsFragment.kt`): (a) add the
   `FilterSeries` enum value(s) + a label; (b) add a `FilterGroup` + `SERIES_COLORS` entry so the chip
   renders; (c) **auto-enable** it — `availableFilters` must include your series when the scenario actually has
   the component (`hasHeatPump`), and any line-only FAB (`showLineFab`) must include your `hasX` so line-only
   components still get the line popup. Both `ChartPoint` builders (single-day and interval) must read your
   field. Missing (c) is why a present component shows no chips.

6. **Sankey reads the row fields DIRECTLY, in BOTH branches (gotcha):** `SankeyView` does not go through
   `ChartPoint` — it accumulates straight from `state.singleDayBarData` **or** `state.intervalData` depending
   on the view, in two separate loops. Add `heatPump += row.heatPump` (and a `Grid → <sink>` flow) to **both**
   branches, or the sink appears in bar/line/area/table but is **missing from the Sankey** in the default view.
   (See the double-counting caveat for scheduled grid sinks — Section 11 / the HP plan TODO.)

7. **Legacy vs UI2:** the legacy graph screens (`scenario/ScenarioMonthly.java`, the bar/line graph
   activities) consume the same model objects, but per the user **UI2 is the only surface that matters** — the
   legacy screens and `ComparisonUIViewModel`/Compare were intentionally skipped for the HP. Add there only if
   asked.

> Adding a column invalidates existing simulation rows by definition — see Section 11.

---

## 8. (covered above) — the dashboard

The dashboard reads the same DAO aggregates as Section 7 (`UI2DashboardViewModel` → KPI query + bar/line
data). There is no separate dashboard datastore; surface your metric by including it in the Section-7 queries
and adding a card/row. Keep the section ordering consistent with the wizard (next section).

---

## 9. Wizard (persisted only)

The wizard is `ui2/UI2WizardActivity.kt` (Compose UI) + `ui2/UI2WizardViewModel.kt` (state + save). For each
component there is a `Wizard…Entry` data class and a section; mirror the battery wiring.

1. **Builder state** — add `val heatPumpEntries: List<WizardHeatPumpEntry>` (or a single entry) to
   `WizardBuilder`.
2. **Entry mapping** — a `WizardHeatPumpEntry` data class with `toHeatPump()` (entry → entity) and a
   `HeatPump.toWizardHeatPumpEntry()` (entity → entry). Mirror `WizardBatteryEntry` (~167).
   - **Gotcha (orphan/junction):** in `toHeatPump()`, *carry* the index for display, but the **edit-save**
     re-add must pass `id = 0` so the DAO re-creates the junction (Section 3). Batteries do
     `entry.toBattery().also { it.batteryIndex = 0 }` in the edit path for exactly this. Load-shift/discharge
     never carry an index, which is why they were never affected — copy that behaviour.
3. **Load on import/copy** — `applyComponentsToBuilder(...)` (~738) populates every `…Entries` from
   `ScenarioComponents`; add `heatPumpEntries = c.heatPumps?.map { it.toWizardHeatPumpEntry() } ?: emptyList()`.
4. **Save** — `save(...)` (~1252) has two paths:
   - **NEW/COPY/LINK:** include your entries in `toScenarioComponents()` (~503) so the bulk insert persists
     them. Guard the per-component work on `newId > 0` (a failed insert returns 0).
   - **EDIT:** delete-existing-then-re-add (~1300), passing `id = 0` on re-add (gotcha above). The single
     `repository.deleteOrphanComponents()` call after the writes prunes the orphans this creates — make sure
     your orphan query is in it (Section 4).
   - Set the `isHasHeatPump` flag in the edit branch's `existing.scenario` update (~1263).
5. **Section composable** — add a `Wizard…Section` and its card, placed to **mirror the dashboard order**
   (canonical: Start → Usage → Inverters → PV → Battery → HW → EV → [your component]). The wizard section
   order must match the dashboard.
6. **Per-section JSON paste** — the wizard supports pasting a JSON fragment per section (the
   `replace…FromJson` setters, ~802). Add a `replaceHeatPumpFromJson(...)` if you want that affordance.

> The wizard adopts the saved id and flips to edit-mode on a successful save (so the name check stops
> flagging the just-saved name, and a re-save updates in place instead of re-inserting). You inherit this — no
> action needed, but don't fight it.

---

## 10. Import / Export

Two distinct mechanisms — make sure your component works in **both**:

- **Per-scenario JSON** (Share button, "paste JSON", `ui2/UI2ImportExportActivity` scenario import): handled
  entirely by your Section 5 JsonTools work. Verify a scenario carrying your component round-trips.
- **Full DB snapshot** (`model/SnapshotExporter.kt` / `SnapshotImporter.kt`): a SQLite-level copy. Because it
  operates on tables, your new `heatpumps` + `scenario2heatpump` tables are carried **automatically** once
  they exist in the schema — *provided* the snapshot's table list / validation includes them. Check
  `SnapshotImporter`'s validation and copy logic; component tables are re-keyed by the same name-clobber rules
  as scenarios. (Data **sources** — `alphaESS*` tables — are the special case there, copied via `ATTACH` and
  keyed by `sysSn`; a scenario component is not a source.)

---

## 11. Costing invalidation (don't skip — it silently rots figures)

**Recompute is missing-only:** the simulator runs only when no sim rows exist, and the cost worker skips
existing costings. So **saving or changing a scenario MUST delete its stale sim + costing rows**, or the
figures go stale forever. The wizard edit path already does this
(`deleteSimulationDataForScenarioID` + `deleteCostingDataForScenarioID`, ~1374). If your component changes
the energy flows (it does), make sure any save path that can alter it triggers that invalidation. Adding the
output column (Section 7) also means any pre-existing sim rows lack it — they must be recomputed.

---

## 12. Tests

- **Component unit test** in `scenario/sim` (plain JUnit4, no Android): exercise your `demand(...)` /
  `absorb(...)` / state-carry directly. `HwComponentTest`, `EvDivertComponentTest`, `ComponentRegistryTest`
  are the templates.
- **Golden master:** the engine is guarded by characterization goldens
  (`app/src/test/resources/sim-golden/*.approved.csv`). A component that is *added* shouldn't change existing
  goldens (they don't use it); add a **new** golden scenario that exercises it. If your change is intended to
  alter an existing flow, that's a reviewed golden delta — regenerate deliberately.
- **JsonTools round-trip** test (Section 5) and, if you touched the wizard save, a quick manual
  paste→save→edit→save→re-export to confirm your component sticks (this is the sequence that surfaced the
  battery edit-save bug).
- Run the scenario subset: `JAVA_HOME=<Android Studio JBR> ./gradlew :app:testDebugUnitTest --tests
  "com.tfcode.comparetout.scenario.*"`. (Two unrelated network tests fail the *full* run; the scenario subset
  is the engine signal.)

---

## 13. The gotchas, collected (read before you start)

1. **Edit-save junction re-link** — re-add components with `id = 0` so the `scenario2…` junction is
   re-created; otherwise the row is orphaned and the component vanishes after an edit. (The battery bug.)
2. **Orphan cleanup** — the delete-and-reinsert pattern leaves orphan rows; ensure your `deleteOrphan…` is in
   `deleteOrphanComponents()`, called once per save.
3. **KPI `SUM` total** — add `+ SUM(yourLoadColumn)` to the KPI/load aggregate (line ~852) or your load is
   invisible to KPIs and costing.
4. **Empty-JSON-object → default** — guard `createX(...)` on a required field; don't let an empty `{}`
   NPE-into a fully-defaulted component.
5. **`scenarioName` UNIQUE** — duplicate-name saves throw a constraint; the DAO now returns `0` and the UI
   gates Save on the name check. Don't reintroduce a bogus-id swallow or an ungated Save.
6. **Costing invalidation** — saving must delete stale sim + costing rows (missing-only recompute).
7. **AutoMigration default** — new non-null columns need a `defaultValue` for the auto-migration to be
   generated.
8. **Wizard ↔ dashboard order** — the wizard section order must mirror the dashboard order.
9. **Byte-identical discipline** — *adding* a component must not perturb existing goldens; if it does, you
   coupled into a shared path you shouldn't have.
10. **Two graph read paths** — populate **both** `ScenarioBarChartData` (single-day) **and** `IntervalRow`
    (windowed/default); forgetting `IntervalRow` shows zeros in the default view and looks like a dead sim.
11. **Shared `IntervalRow` ⇒ importer placeholders** — `IntervalRow` is shared with importer queries; every
    importer aggregation query must emit `0 AS YOURCOL` or Room won't compile.
12. **Sum vs avg** — energies `SUM`, driver/state metrics `AVG` (inner-`SUM`/outer-`AVG`); summing a COP/temp
    multiplies it by the bucket size.
13. **Sankey reads rows directly, in both branches** — add your accumulation to *both* the `singleDayBarData`
    and `intervalData` loops, or the sink is missing from the default-view Sankey.
14. **Filter auto-enable** — adding the `FilterSeries` enum isn't enough; `availableFilters` (and `showLineFab`
    for line-only metrics) must include your `hasX` flag or no chip renders.
15. **JSON empty-field omission** — emit your JSON array/object only when non-empty (`null`, not `[]`/`{}`),
    or pre-existing scenarios stop serialising byte-identically and the round-trip test breaks.

---

## 14. Heat-pump touch-point map (the next user — not built here)

| Step | Heat-pump specifics |
|---|---|
| 1. Engine | `HeatPumpComponent : DemandContributor` (electrical = thermal demand ÷ COP, clamped to capacity, with comfort-band droop); optionally `SurplusSink` (dump surplus PV into the thermal store). Thermal state carried internally like HW. One line in `ComponentRegistry.build`. |
| 2. Schema | `HeatPump` entity + `Scenario2HeatPump` junction; `isHasHeatPump` flag; DB v8 → v9 + `@AutoMigration`. |
| 5. JSON | `HeatPumpJson` + `ScenarioJsonFile.heatPumps` + JsonTools both directions. |
| 6. Engine input | **Outdoor temperature** series — its own ingestion/derivation path (measured series vs monthly profile is an open decision); a heating usage profile distributes annual demand across months/hours. |
| 7. Output/graphs | `heatPumpLoad` column; add to KPI `SUM`, bar data, and dashboard/compare. Open: also a thermal-state column for line graphs? |
| 9. Wizard | a Heat Pump section placed after EV (mirroring the dashboard); fuel-use → annual kWh, COP, capacity, schedule, comfort band. |
| 11. Costing | invalidate on add/edit (inherited from the wizard save path). |

Open decisions deferred to the HP's own plan: external temperature (measured vs profile for v1); HP divert in
v1 or demand-only first; COP constant vs temperature-dependent table; single `heatPumpLoad` column vs also a
thermal-state column. See `plans/sim/component.md` § "Deferred — heat pump".

---

### File map (where each step lives)

| Step | Files |
|---|---|
| Engine seam | `scenario/sim/{DemandContributor,SurplusSink,Generator,Storage,Converter,GridExchange,InverterComponent,OutputChannel,IntervalContext,ComponentRegistry}.java`, `scenario/SimulationEngine.java`, `scenario/ScenarioInputs.java`, `scenario/SimulationWorker.java` |
| Schema/DAO | `model/scenario/{HeatPump,Scenario2HeatPump,Scenario,ScenarioComponents,ScenarioSimulationData}.java`, `model/ToutcDB.java`, `model/ScenarioDAO.java`, `model/ToutcRepository.java` |
| JSON | `model/json/scenario/{HeatPumpJson,ScenarioJsonFile}.java`, `model/json/JsonTools.java` |
| Wizard | `ui2/UI2WizardViewModel.kt`, `ui2/UI2WizardActivity.kt` |
| Graphs/Compare/Dashboard | `model/scenario/{ScenarioBarChartData,ScenarioLineGraphData}.java`, `scenario/ScenarioMonthly.java`, `ui2/{UI2GraphsViewModel,UI2DashboardViewModel}.kt`, `ComparisonUIViewModel.java` |
| Import/Export | `model/{SnapshotExporter,SnapshotImporter}.kt`, `ui2/UI2ImportExportActivity.kt` |
| Costing | `CostingWorker.java`, the wizard save invalidation |
| Tests | `app/src/test/java/com/tfcode/comparetout/scenario/sim/…`, `app/src/test/resources/sim-golden/…`, `model/json/ScenarioImportParseTest.java` |
