# Database dependencies by component

**Forward map: given a screen, worker or package, which tables does it touch?**
For the reverse direction — *what breaks if I change this table* — see
[db-dependencies-by-table.md](db-dependencies-by-table.md).

Accurate as of 2026-08-09 (database v17). Table columns below are **derived from
source** by `scripts/depmap.py`; the commentary is hand-written.

> **Read the derived tables as a floor, not a ceiling.** The extractor follows
> `ToutcRepository` delegation into DAO methods and reads their SQL. Where a
> repository method fans out through a `model/ops/*` composite operation, the
> extractor sees the entry point but not everything the operation reaches. The
> clearest case is `SimulationWorker`, listed below as writing
> `scenariosimulationdata`: it *reads* essentially every component table through
> composite loaders. Where this matters, the commentary says so.

## Access paths

Everything reaches the database through `ToutcRepository`. Two ways in:

1. **UI** → ViewModel → `ToutcRepository` → ops/DAO → Room
2. **Worker** → `ToutcRepository` (constructed directly) → ops/DAO → Room

Workers bypass the ViewModel layer deliberately: background work must survive UI
destruction.

## UI2 — Jetpack Compose (current surface)

| ViewModel / class | Tables | Notes |
|---|---|---|
| `ui2/UI2WizardViewModel.kt` | 30 tables — every component, every junction, plus `scenarios`, `loadprofiledata`, `paneldata`, `scenario_readiness`, `scenariosimulationdata`, `costings` | The widest surface in the app: builds and edits a whole scenario, so it touches everything a scenario owns. Section order is canonical — Start → Usage → Inverters → PV → Battery → HW → EV — and must mirror the dashboard. |
| `ui2/UI2DirectorViewModel.kt` | `panels`, `paneldata`, `scenario2battery`, `scenario2discharge`, `scenario2evcharge`, `scenario2evdivert`, `scenario2heatpump`, `scenario2loadshift` | Component-level direction/dispatch editing. |
| `ui2/UI2CompareViewModel.kt` | `costings`, `DayRates`, `scenariosimulationdata` | Reads annual scalars and smears them for graphs — **no per-bucket cost is stored**. Filter availability is string-driven from importer capability. |
| `ui2/UI2DashboardViewModel.kt` | `costings`, `DayRates`, `scenariosimulationdata` | Mirrors Compare's costing reads. |
| `ui2/UI2PricePlanListViewModel.kt` | `PricePlans`, `DayRates`, `costings`, `scenario_readiness` | List, activate, delete. Deleting must invalidate dependent costings. |
| `ui2/UI2PricePlanViewModel.kt` | `DayRates`, `costings`, `scenario_readiness` | Plan editor. Partitions rates by `rateType` against the plan's `direction`, carrying the other direction's rates through untouched. |
| `ui2/UI2GraphsViewModel.kt` | `scenariosimulationdata` | Aggregation only. |
| `ui2/UI2SimulationsViewModel.kt` | `DayRates` | Simulation status / launch surface. |
| `ui2/UI2DataSourceManagementViewModel.kt` | `alphaESSTransformedData`, `alphaESSTransformMeta` | All sources, one screen; per-source sections live in `ui2/Source*.kt`. |
| `ui2/UI2SharedViewModel.kt` | `alphaESSTransformedData` | Cross-screen shared state. |
| `ui2/UI2HaBackfillViewModel.kt` | — (enqueues `HABackfillWorker`) | Multi-step push wizard. Experimental-gated. |
| `ui2/UI2SimpleViewModel.kt` | — | Planned single-screen "does it pay off?" mode. |
| `ui2/UI2ImportExportActivity.kt` | `costings`, `scenario_readiness` | JSON import/export; importing must invalidate. |

Supporting non-UI classes in `ui2/`:

| Class | Tables | Role |
|---|---|---|
| `ui2/OctopusTariffPlans.kt` | `costings`, `scenario_readiness` | Turns Octopus products into price plans; adopts the user's current plan as favourite. |
| `ui2/DynamicTariffPlans.kt` | `costings`, `scenario_readiness` | Materialises a wholesale-tracking tariff into 365 single-day rate sets. |
| `ui2/StrategyScenarioGenerator.kt` | `PricePlans`, `DayRates` | Emits generated "⚡" scenarios from tariff strategies. |
| `ui2/PricePlanDownloader.kt` | `costings`, `scenario_readiness` | Published plan bundles. |
| `ui2/SampleDataLoader.kt`, `ui2/SimpleScenarioLoader.kt` | `costings`, `scenario_readiness`, `scenariosimulationdata` | First-run and simple-mode seeding. |

## UI1 — Views + Fragments (still shipping)

All UI1 screens share one ViewModel:

| Class | Tables |
|---|---|
| `ComparisonUIViewModel.java` | `scenarios`, `PricePlans`, `DayRates`, `costings`, `scenariosimulationdata`, `panels`, `paneldata`, `alphaESSTransformedData`, `scenario_readiness`, and the `scenario2*` junctions |

Eighteen activities under `scenario/`, `priceplan/`, `importers/` and `util/`
funnel through it, each with fragments for the component they edit
(`BatterySettingsFragment`, `PanelFragment`, `EVDivertFragment`,
`LoadProfile*DistributionFragment`, `PricePlanEditFragment`, …). Their table
dependencies are the subset of the row above matching their component — a battery
fragment reaches `batteries` + `scenario2battery`, and so on.

Three UI1 classes bypass the shared ViewModel and hold the repository directly:

| Class | Tables |
|---|---|
| `importers/ImportOverviewFragment.java` | `PricePlans`, `DayRates` |
| `scenario/ScenarioSelectDialog.java` | `scenarios` |
| `util/CompareScenarioSelectDialog.java` | `scenarios` |

## Workers

### Simulation and costing

| Worker | Tables | Notes |
|---|---|---|
| `scenario/SimulationWorker.java` | writes `scenariosimulationdata`; **reads every component table** via composite loaders | Skips scenarios missing panel data or weather — so any worker that supplies missing data must call `simulateIfNeeded` on success, or the scenario stays unsimulated forever. |
| `CostingWorker.java` | `costings`, `PricePlans`, `DayRates`, `scenarios`, `loadprofile` | Decomposed: one export pass + one import pass, then arithmetic. N×M plan pairs cost N+M passes. Already-costed pairs are skipped **before** the expensive pass, not after. |
| `scenario/loadprofile/GenerateMissingLoadDataWorker.java` | `loadprofile`, `loadprofiledata` | |
| `scenario/loadprofile/DeleteLoadDataFromProfileWorker.java` | `loadprofiledata` | |

### Import — AlphaESS

| Worker | Tables |
|---|---|
| `importers/alphaess/ImportWorker.java` | `alphaESSRawEnergy`, `alphaESSRawPower`, `alphaESSTransformedData` |
| `importers/alphaess/CatchUpWorker.java` | the above + `alphaESSTransformMeta` |
| `importers/alphaess/DailyWorker.java` | the above + `alphaESSTransformMeta` |
| `importers/alphaess/ExportWorker.java` | `alphaESSRawEnergy`, `alphaESSRawPower` |
| `importers/alphaess/AlphaESSMigrationWorker.java` | all three raw/transformed tables |
| `importers/alphaess/GenerationWorker.java` | `alphaESSRawPower` + scenario creation via base class |

### Import — other sources

| Worker | Tables | Experimental gate |
|---|---|---|
| `importers/esbn/ESBNImportWorker.java` | `alphaESSTransformedData` | **No** — HDF is a published format |
| `importers/esbn/ESBNCatchUpWorker.java` | `alphaESSTransformedData` | Yes — scraped cloud sync |
| `importers/esbn/ESBNExportWorker.java` | `alphaESSTransformedData` | No |
| `importers/homeassistant/HACatchupWorker.java` | `alphaESSTransformedData` | **No** — official API |
| `importers/homeassistant/HABackfillWorker.java` | reads `alphaESSTransformedData`; **writes into Home Assistant, not the local database** | Yes — unproven push |
| `importers/octopus/OctopusCatchUpWorker.java` | `alphaESSTransformedData` | No |
| `importers/octopus/OctopusCsvImportWorker.java` | `alphaESSTransformedData` | No |
| `importers/solis/SolisCatchUpWorker.java` | `alphaESSRawEnergy`, `alphaESSTransformedData` | Yes |
| `importers/fusionsolar/FusionSolarCatchUpWorker.java` | `alphaESSRawEnergy`, `alphaESSTransformedData` | Yes |

`importers/AbstractGenerationWorker.java` (`loadprofile`, `loadprofiledata`,
`paneldata`, `scenarios`) is the shared base for every source's "generate a
scenario from my imported data" flow.

### Weather, PV and tariff data

| Worker | Tables |
|---|---|
| `scenario/panel/PVGISLoader.java` | `panels`, `paneldata` |
| `ui2/PVGISDirectFetchWorker.kt` | `panels`, `paneldata` |
| `ui2/PanelSourceFetchWorker.kt` | `paneldata` |
| `model/PanelDataRefreshWorker.java` | `paneldata` |
| `ui2/HeatPumpWeatherFetchWorker.kt` | `scenario_readiness` |
| `ui2/DynamicTariffWorker.kt` | `costings`, `scenario_readiness` |
| `model/TimezoneRestampWorker.java` | re-stamps imported series to UTC millis |

## Repository, ops and DAO layer

| Layer | Location | Role |
|---|---|---|
| Facade | `model/ToutcRepository.java` — 1273 lines, 215 public methods | The only door to the database. |
| Composite ops | `model/ops/*` — 10 classes, ~1500 lines | Multi-table logical acts. `ScenarioLifecycleOps` (384 lines) covers create/copy/delete across ~25 tables. |
| DAOs | `model/dao/*` (10) + `model/{Scenario,PricePlan,Costing,AlphaEss}DAO.java` (4) | 205 methods total. See [architecture.md](architecture.md#layers) for the per-DAO breakdown. |
| JSON | `model/json/*` | Import/export facade, split per domain. |
| Costing types | `model/costings/` | `Costings` (annual scalars) + `SubTotals`. |

`model/SnapshotImporter.kt` sits alongside these and reaches DAOs directly for
whole-database snapshot restore.

## Known extraction gaps

`scripts/depmap.py` reported these callers as touching the repository without
resolving to tables. They are real dependencies the two-hop join could not prove,
recorded here rather than silently dropped:

- `ui2/SourceModels.kt`, `ui2/WizardBuilderModels.kt` — state holders whose
  repository calls arrive as lambdas
- `ui2/AppModule.kt` — Hilt provision, no queries of its own
- `model/ops/CombinationOps.java` — reaches `plan_combinations` through
  `CombinationDAO`; the extractor does not traverse ops classes
- `importers/homeassistant/GenerationWorker.java` — scenario creation inherited
  from `AbstractGenerationWorker`
