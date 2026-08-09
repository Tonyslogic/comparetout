# Database dependencies by table

**Reverse map: given a table, what depends on it?** Use this before changing a
column, an index, or a table name. For the forward direction see
[db-dependencies.md](db-dependencies.md).

Accurate as of 2026-08-09 (database v17, 37 tables). **Derived from source** by
`scripts/depmap.py` — regenerate after any DAO or repository change rather than
editing this table by hand.

## How to use this

1. Find your table.
2. Open every class in the Dependents column — those are the call sites that can
   break at compile time or behave differently at runtime.
3. Check the [caveats](#caveats) below, because the derived list is a floor.
4. Run `:app:testIeDebugUnitTest`, and if the change alters stored values, check
   whether simulation or costing rows need invalidating.

## Caveats

The extractor follows `ToutcRepository` delegation into DAO methods and reads
their SQL. It cannot see:

- Fan-out through `model/ops/*` composite operations. Component tables that show
  a single dependent (`batteries`, `inverters`, `evcharge`, `heatpumps`,
  `hwsystem`, …) are reached by far more code than the count suggests —
  scenario create/copy/delete in `ScenarioLifecycleOps`, and `SimulationWorker`
  reading components through composite loaders.
- `plan_combinations`, which resolves to no callers here because its only access
  path is `CombinationOps` → `CombinationDAO`.
- UI1 fragments, which reach tables through `ComparisonUIViewModel` and so appear
  under that one entry rather than individually.

A dependent count of 1 therefore means *one proven direct path*, not *safe to
change freely*.

## Dependencies

| Table | DAOs | Dependents | Classes |
|---|---|---:|---|
| `alphaESSRawEnergy` | `AlphaEssDAO` | 7 |<br>`importers/alphaess/AlphaESSMigrationWorker.java` <br>`importers/alphaess/CatchUpWorker.java` <br>`importers/alphaess/DailyWorker.java` <br>`importers/alphaess/ExportWorker.java` <br>`importers/alphaess/ImportWorker.java` <br>`importers/fusionsolar/FusionSolarCatchUpWorker.java` <br>`importers/solis/SolisCatchUpWorker.java` |
| `alphaESSRawPower` | `AlphaEssDAO` | 6 |<br>`importers/alphaess/AlphaESSMigrationWorker.java` <br>`importers/alphaess/CatchUpWorker.java` <br>`importers/alphaess/DailyWorker.java` <br>`importers/alphaess/ExportWorker.java` <br>`importers/alphaess/GenerationWorker.java` <br>`importers/alphaess/ImportWorker.java` |
| `alphaESSTransformedData` | `AlphaEssDAO` | 15 |<br>`ComparisonUIViewModel.java` <br>`importers/alphaess/AlphaESSMigrationWorker.java` <br>`importers/alphaess/CatchUpWorker.java` <br>`importers/alphaess/DailyWorker.java` <br>`importers/alphaess/ImportWorker.java` <br>`importers/esbn/ESBNCatchUpWorker.java` <br>`importers/esbn/ESBNExportWorker.java` <br>`importers/esbn/ESBNImportWorker.java` <br>`importers/fusionsolar/FusionSolarCatchUpWorker.java` <br>`importers/homeassistant/HACatchupWorker.java` <br>`importers/octopus/OctopusCatchUpWorker.java` <br>`importers/octopus/OctopusCsvImportWorker.java` <br>`importers/solis/SolisCatchUpWorker.java` <br>`ui2/UI2DataSourceManagementViewModel.kt` <br>`ui2/UI2SharedViewModel.kt` |
| `alphaESSTransformMeta` | `AlphaEssDAO` | 3 |<br>`importers/alphaess/CatchUpWorker.java` <br>`importers/alphaess/DailyWorker.java` <br>`ui2/UI2DataSourceManagementViewModel.kt` |
| `batteries` | `BatteryDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `costings` | `CostingDAO`, `ScenarioDAO`, `SimDataDAO` | 14 |<br>`ComparisonUIViewModel.java` <br>`CostingWorker.java` <br>`ui2/DynamicTariffPlans.kt` <br>`ui2/DynamicTariffWorker.kt` <br>`ui2/OctopusTariffPlans.kt` <br>`ui2/PricePlanDownloader.kt` <br>`ui2/SampleDataLoader.kt` <br>`ui2/SimpleScenarioLoader.kt` <br>`ui2/UI2CompareViewModel.kt` <br>`ui2/UI2DashboardViewModel.kt` <br>`ui2/UI2ImportExportActivity.kt` <br>`ui2/UI2PricePlanListViewModel.kt` <br>`ui2/UI2PricePlanViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `DayRates` | `PricePlanDAO` | 9 |<br>`ComparisonUIViewModel.java` <br>`CostingWorker.java` <br>`importers/ImportOverviewFragment.java` <br>`ui2/StrategyScenarioGenerator.kt` <br>`ui2/UI2CompareViewModel.kt` <br>`ui2/UI2DashboardViewModel.kt` <br>`ui2/UI2PricePlanListViewModel.kt` <br>`ui2/UI2PricePlanViewModel.kt` <br>`ui2/UI2SimulationsViewModel.kt` |
| `discharge2grid` | `BatteryDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `evcharge` | `EvDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `evdivert` | `EvDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `heatpumps` | `HeatPumpDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `hwdivert` | `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `hwschedule` | `HotWaterDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `hwsystem` | `HotWaterDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `inverters` | `InverterDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `loadprofile` | `LoadProfileDAO`, `ScenarioDAO` | 5 |<br>`CostingWorker.java` <br>`importers/AbstractGenerationWorker.java` <br>`importers/esbn/GenerationWorker.java` <br>`scenario/loadprofile/GenerateMissingLoadDataWorker.java` <br>`ui2/UI2WizardViewModel.kt` |
| `loadprofiledata` | `LoadProfileDAO`, `ScenarioDAO` | 5 |<br>`importers/AbstractGenerationWorker.java` <br>`importers/esbn/GenerationWorker.java` <br>`scenario/loadprofile/DeleteLoadDataFromProfileWorker.java` <br>`scenario/loadprofile/GenerateMissingLoadDataWorker.java` <br>`ui2/UI2WizardViewModel.kt` |
| `loadshift` | `BatteryDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `paneldata` | `PanelDAO`, `ScenarioDAO` | 7 |<br>`ComparisonUIViewModel.java` <br>`importers/AbstractGenerationWorker.java` <br>`scenario/panel/PVGISLoader.java` <br>`ui2/PVGISDirectFetchWorker.kt` <br>`ui2/PanelSourceFetchWorker.kt` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `panels` | `PanelDAO`, `ScenarioDAO` | 5 |<br>`ComparisonUIViewModel.java` <br>`scenario/panel/PVGISLoader.java` <br>`ui2/PVGISDirectFetchWorker.kt` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `plan_combinations` | `CombinationDAO` | 0 |_none resolved_ |
| `PricePlans` | `PricePlanDAO` | 5 |<br>`ComparisonUIViewModel.java` <br>`CostingWorker.java` <br>`importers/ImportOverviewFragment.java` <br>`ui2/StrategyScenarioGenerator.kt` <br>`ui2/UI2PricePlanListViewModel.kt` |
| `scenario2battery` | `BatteryDAO`, `ScenarioDAO` | 3 |<br>`ComparisonUIViewModel.java` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario2discharge` | `BatteryDAO`, `ScenarioDAO` | 3 |<br>`ComparisonUIViewModel.java` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario2evcharge` | `EvDAO`, `ScenarioDAO` | 3 |<br>`ComparisonUIViewModel.java` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario2evdivert` | `EvDAO`, `ScenarioDAO` | 3 |<br>`ComparisonUIViewModel.java` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario2heatpump` | `HeatPumpDAO`, `ScenarioDAO` | 2 |<br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario2hwdivert` | `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `scenario2hwschedule` | `HotWaterDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `scenario2hwsystem` | `HotWaterDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `scenario2inverter` | `InverterDAO`, `ScenarioDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `scenario2loadprofile` | `LoadProfileDAO`, `ScenarioDAO`, `SimDataDAO` | 1 |<br>`ui2/UI2WizardViewModel.kt` |
| `scenario2loadshift` | `BatteryDAO`, `ScenarioDAO` | 3 |<br>`ComparisonUIViewModel.java` <br>`ui2/UI2DirectorViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario2panel` | `PanelDAO`, `ScenarioDAO` | 2 |<br>`ComparisonUIViewModel.java` <br>`ui2/UI2WizardViewModel.kt` |
| `scenario_readiness` | `ReadinessDAO`, `ScenarioDAO` | 12 |<br>`ComparisonUIViewModel.java` <br>`ui2/DynamicTariffPlans.kt` <br>`ui2/DynamicTariffWorker.kt` <br>`ui2/HeatPumpWeatherFetchWorker.kt` <br>`ui2/OctopusTariffPlans.kt` <br>`ui2/PricePlanDownloader.kt` <br>`ui2/SampleDataLoader.kt` <br>`ui2/SimpleScenarioLoader.kt` <br>`ui2/UI2ImportExportActivity.kt` <br>`ui2/UI2PricePlanListViewModel.kt` <br>`ui2/UI2PricePlanViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
| `scenarios` | `BatteryDAO`, `EvDAO`, `HeatPumpDAO`, `HotWaterDAO`, `InverterDAO`, `LoadProfileDAO`, `PanelDAO`, `ReadinessDAO`, `ScenarioDAO` | 6 |<br>`ComparisonUIViewModel.java` <br>`CostingWorker.java` <br>`importers/AbstractGenerationWorker.java` <br>`importers/esbn/GenerationWorker.java` <br>`scenario/ScenarioSelectDialog.java` <br>`ui2/UI2WizardViewModel.kt` |
| `scenariosimulationdata` | `ScenarioDAO`, `SimDataDAO` | 7 |<br>`ComparisonUIViewModel.java` <br>`scenario/SimulationWorker.java` <br>`ui2/SimpleScenarioLoader.kt` <br>`ui2/UI2CompareViewModel.kt` <br>`ui2/UI2DashboardViewModel.kt` <br>`ui2/UI2GraphsViewModel.kt` <br>`ui2/UI2WizardViewModel.kt` |
