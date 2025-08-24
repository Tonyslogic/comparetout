# Database Dependencies by Activity/Package

This document outlines the database dependencies for each activity and package in the CompareToUT application. The architecture supports two main invocation patterns:

1. **UI Components** → ComparisonUIViewModel → ToutcRepository → DAO classes → Database Tables
2. **Worker Classes** → ToutcRepository (direct) → DAO classes → Database Tables

## Complete Database Tables List

Based on the @Entity annotations, the application uses 32 database tables:

**Core Tables:**
- `scenarios` - Main scenario definitions
- `PricePlans` - Electricity tariff plans  
- `DayRates` - Time-based rates within price plans
- `costings` - Cost calculation results

**Component Tables:**
- `batteries` - Energy storage systems
- `inverters` - Power conversion equipment  
- `panels` - Solar panel arrays
- `hwsystem` - Hot water systems
- `loadprofile` - Electricity consumption patterns
- `evcharge` - Electric vehicle charging profiles
- `evdivert` - EV surplus diversion settings
- `hwschedule` - Hot water heating schedules
- `hwdivert` - Hot water surplus diversion
- `loadshift` - Load shifting configurations
- `discharge2grid` - Grid discharge settings

**Junction Tables (Many-to-Many Relationships):**
- `scenario2battery` - Links scenarios to batteries
- `scenario2inverter` - Links scenarios to inverters
- `scenario2panel` - Links scenarios to panels
- `scenario2hwsystem` - Links scenarios to hot water systems
- `scenario2loadprofile` - Links scenarios to load profiles
- `scenario2evcharge` - Links scenarios to EV charging
- `scenario2evdivert` - Links scenarios to EV diversion
- `scenario2hwschedule` - Links scenarios to HW schedules
- `scenario2hwdivert` - Links scenarios to HW diversion
- `scenario2loadshift` - Links scenarios to load shifting
- `scenario2discharge` - Links scenarios to grid discharge

**Data/Analysis Tables:**
- `loadprofiledata` - Detailed load profile measurements
- `paneldata` - Solar panel generation data
- `scenariosimulationdata` - Time-series simulation results

**Import Tables:**
- `alphaESSRawEnergy` - Raw daily energy data from AlphaESS
- `alphaESSRawPower` - Raw 5-minute power data from AlphaESS  
- `alphaESSTransformedData` - Processed energy data for analysis

**Note:** All import systems (AlphaESS, ESBN, HomeAssistant) use the AlphaESS table structure as a shared format. The table names were not refactored when additional import types were added, so all importers store their data in the `alphaESS*` tables regardless of the original data source.

## Activity/Package to Database Dependencies

### UI Components (via ComparisonUIViewModel)

| Activity/Package | DB Tables | DAO Access | DAO Methods Used |
|------------------|-----------|------------|------------------|
| **Main Comparison** | `scenarios`, `costings` | ScenarioDAO, CostingDAO | `loadScenarios()`, `loadCostings()`, `getBestCostingForScenario()` |
| **Price Plan Management** | `PricePlans`, `DayRates` | PricePlanDAO | `loadPricePlans()`, `addNewPricePlanWithDayRates()`, `updatePricePlanWithDayRates()`, `deletePricePlan()`, `updatePricePlanActiveStatus()` |
| **Scenario Management** | `scenarios`, `scenario2*` (all junction tables) | ScenarioDAO | `addNewScenarioWithComponents()`, `updateScenario()`, `deleteScenario()`, `copyScenario()`, `updateScenarioActiveStatus()` |
| **Battery Package** | `batteries`, `scenario2battery` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()`, `copyBatteryFromScenario()`, `linkBatteryFromScenario()`, `deleteBatteryFromScenario()` |
| **Panel Package** | `panels`, `scenario2panel`, `paneldata` | ScenarioDAO | `savePanel()`, `getPanelsForScenarioID()`, `copyPanelFromScenario()`, `linkPanelFromScenario()`, `deletePanelFromScenario()`, `savePanelData()`, `removePanelData()` |
| **Inverter Package** | `inverters`, `scenario2inverter` | ScenarioDAO | `saveInverter()`, `getInvertersForScenarioID()`, `copyInverterFromScenario()`, `linkInverterFromScenario()`, `deleteInverterFromScenario()` |
| **EV Package** | `evcharge`, `evdivert`, `scenario2evcharge`, `scenario2evdivert` | ScenarioDAO | `saveEVChargeForScenario()`, `getEVChargesForScenarioID()`, `saveEVDivertForScenario()`, `getEVDivertForScenarioID()`, `deleteEVChargeFromScenario()`, `deleteEVDivertFromScenario()` |
| **Water Package** | `hwsystem`, `hwschedule`, `hwdivert`, `scenario2hwsystem`, `scenario2hwschedule`, `scenario2hwdivert` | ScenarioDAO | `saveHWSystemForScenario()`, `getHWSystemForScenarioID()`, `saveHWScheduleForScenario()`, `getHWSchedulesForScenarioID()`, `saveHWDivert()`, `getHWDivertForScenarioID()` |
| **Load Profile Package** | `loadprofile`, `loadprofiledata`, `scenario2loadprofile` | ScenarioDAO | `saveLoadProfile()`, `getLoadProfileForScenarioID()`, `copyLoadProfileFromScenario()`, `linkLoadProfileFromScenario()`, `createLoadProfileDataEntries()`, `deleteLoadProfileData()` |
| **Load Shift Package** | `loadshift`, `scenario2loadshift` | ScenarioDAO | `saveLoadShiftForScenario()`, `getLoadShiftsForScenarioID()`, `copyLoadShiftFromScenario()`, `linkLoadShiftFromScenario()`, `deleteLoadShiftFromScenario()` |
| **Discharge to Grid** | `discharge2grid`, `scenario2discharge` | ScenarioDAO | `saveDischargeForScenario()`, `getDischargesForScenarioID()`, `copyDischargeFromScenario()`, `linkDischargeFromScenario()`, `deleteDischargeFromScenario()` |

### UI Fragments (via ComparisonUIViewModel)

| Fragment | DB Tables | DAO Access | DAO Methods Used |
|----------|-----------|------------|------------------|
| **ComparisonFragment** | `scenarios`, `costings` | ScenarioDAO, CostingDAO | `loadScenarios()`, `loadCostings()` |
| **ScenarioNavFragment** | `scenarios` | ScenarioDAO | `getAllScenarios()` |
| **PricePlanNavFragment** | `PricePlans` | PricePlanDAO | `loadPricePlans()` |
| **BatterySettingsFragment** | `batteries`, `scenario2battery` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()` |
| **BatteryChargingFragment** | `batteries`, `scenario2battery` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()` |
| **BatteryDischargeFragment** | `batteries`, `scenario2battery`, `discharge2grid`, `scenario2discharge` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()`, `saveDischargeForScenario()`, `getDischargesForScenarioID()` |
| **PanelFragment** | `panels`, `scenario2panel`, `paneldata` | ScenarioDAO | `savePanel()`, `getPanelsForScenarioID()`, `savePanelData()` |
| **InverterFragment** | `inverters`, `scenario2inverter` | ScenarioDAO | `saveInverter()`, `getInvertersForScenarioID()` |
| **EVScheduleFragment** | `evcharge`, `scenario2evcharge` | ScenarioDAO | `saveEVChargeForScenario()`, `getEVChargesForScenarioID()` |
| **EVDivertFragment** | `evdivert`, `scenario2evdivert` | ScenarioDAO | `saveEVDivertForScenario()`, `getEVDivertForScenarioID()` |
| **WaterScheduleFragment** | `hwschedule`, `scenario2hwschedule` | ScenarioDAO | `saveHWScheduleForScenario()`, `getHWSchedulesForScenarioID()` |
| **LoadProfilePropertiesFragment** | `loadprofile`, `scenario2loadprofile` | ScenarioDAO | `saveLoadProfile()`, `getLoadProfileForScenarioID()` |
| **LoadProfileDailyDistributionFragment** | `loadprofiledata` | ScenarioDAO | `getLoadProfileDataForScenario()`, `createLoadProfileDataEntries()` |
| **LoadProfileHourlyDistributionFragment** | `loadprofiledata` | ScenarioDAO | `getLoadProfileDataForScenario()`, `createLoadProfileDataEntries()` |
| **LoadProfileMonthlyDistributionFragment** | `loadprofiledata` | ScenarioDAO | `getLoadProfileDataForScenario()`, `createLoadProfileDataEntries()` |
| **PricePlanEditFragment** | `PricePlans`, `DayRates` | PricePlanDAO | `addNewPricePlanWithDayRates()`, `updatePricePlanWithDayRates()`, `loadPricePlans()` |
| **PricePlanEditDayFragment** | `DayRates` | PricePlanDAO | `getAllDayRatesForPricePlanID()`, `updateDayRate()` |

### Import Fragments (Direct Repository Access)

| Fragment | DB Tables | DAO Access | DAO Methods Used |
|----------|-----------|------------|------------------|
| **ImportOverviewFragment** | `PricePlans`, `DayRates` | PricePlanDAO | `getAllPricePlansNow()`, `getAllDayRatesForPricePlanID()` |
| **ImportGenerateScenarioFragment** | `scenarios` | ScenarioDAO | `addNewScenarioWithComponents()` |
| **ImportKeyStatsFragment** | `alphaESSTransformedData`, `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | `getKeyStats()`, `getKPIs()`, `getLiveDateRanges()` |
| **BaseGraphsFragment** | `alphaESSTransformedData`, `scenariosimulationdata` | AlphaEssDAO, ScenarioDAO | `getLiveDateRanges()`, `getSumHour()`, `getSumDOY()`, `getSumDOW()`, `getSumMonth()`, `getSumYear()`, `getAvgHour()`, `getAvgDOY()`, `getAvgDOW()`, `getAvgMonth()`, `getAvgYear()` |
| **ScenarioDetails** | `costings`, `scenariosimulationdata` | CostingDAO, ScenarioDAO | `getAllComparisons()`, `getBarData()`, `getLineData()` |
| **ScenarioGraphs** | `scenariosimulationdata`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | Graph display methods from BaseGraphsFragment |
| **ScenarioOverview** | `scenarios`, `costings` | ScenarioDAO, CostingDAO | `loadScenarios()`, `getAllComparisons()` |
| **ScenarioMonthly** | `scenariosimulationdata` | ScenarioDAO | `getMonthlyData()` |
| **ScenarioYear** | `scenariosimulationdata` | ScenarioDAO | `getYearlyData()` |
| **ImportAlphaOverview** | `alphaESSRawEnergy`, `alphaESSTransformedData` | AlphaEssDAO | `getDateRanges()`, `getSystemSNs()` |
| **ImportAlphaKeyStats** | `alphaESSTransformedData`, `alphaESSRawEnergy` | AlphaEssDAO | `getKeyStats()`, `getKPIs()` |
| **ImportAlphaGraphs** | `alphaESSTransformedData` | AlphaEssDAO | All BaseGraphsFragment methods |
| **ImportAlphaGenerateScenario** | `scenarios`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `addNewScenarioWithComponents()`, `getAvgHour()` |
| **ImportESBNOverview** | `alphaESSRawEnergy`, `alphaESSTransformedData` | AlphaEssDAO | `getDateRanges()`, `getSystemSNs()` (uses AlphaESS tables) |
| **ImportESBNGraphs** | `alphaESSTransformedData` | AlphaEssDAO | All BaseGraphsFragment methods (uses AlphaESS tables) |
| **ImportESBNGenerateScenario** | `scenarios`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `addNewScenarioWithComponents()`, `getAvgHour()` (uses AlphaESS tables) |
| **ImportHAOverview** | `alphaESSRawEnergy`, `alphaESSTransformedData` | AlphaEssDAO | `getDateRanges()`, `getSystemSNs()` (uses AlphaESS tables) |
| **ImportHAKeyStats** | `alphaESSTransformedData`, `alphaESSRawEnergy` | AlphaEssDAO | `getKeyStats()`, `getKPIs()` (uses AlphaESS tables) |
| **ImportHAGraphs** | `alphaESSTransformedData` | AlphaEssDAO | All BaseGraphsFragment methods (uses AlphaESS tables) |
| **ImportHAGenerateScenario** | `scenarios`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `addNewScenarioWithComponents()`, `getAvgHour()` (uses AlphaESS tables) |
| **CompareScenarioSelectDialog** | `scenarios` | ScenarioDAO | `loadScenarios()` |
| **ScenarioSelectDialog** | `scenarios` | ScenarioDAO | `loadScenarios()` |

### Activities (via ComparisonUIViewModel)

| Activity | DB Tables | DAO Access | DAO Methods Used |
|----------|-----------|------------|------------------|
| **MainActivity** | `scenarios`, `costings`, `PricePlans` | ScenarioDAO, CostingDAO, PricePlanDAO | `loadScenarios()`, `loadCostings()`, `loadPricePlans()` |
| **PricePlanActivity** | `PricePlans`, `DayRates` | PricePlanDAO | `loadPricePlans()`, `addNewPricePlanWithDayRates()`, `updatePricePlanWithDayRates()`, `deletePricePlan()` |
| **ScenarioActivity** | `scenarios`, all `scenario2*` tables | ScenarioDAO | `loadScenarios()`, `addNewScenarioWithComponents()`, `updateScenario()`, `deleteScenario()` |
| **BatterySettingsActivity** | `batteries`, `scenario2battery` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()` |
| **BatteryChargingActivity** | `batteries`, `scenario2battery` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()` |
| **BatteryDischargeActivity** | `batteries`, `scenario2battery`, `discharge2grid`, `scenario2discharge` | ScenarioDAO | `saveBatteryForScenario()`, `getBatteriesForScenarioID()`, `saveDischargeForScenario()`, `getDischargesForScenarioID()` |
| **InverterActivity** | `inverters`, `scenario2inverter` | ScenarioDAO | `saveInverter()`, `getInvertersForScenarioID()` |
| **PanelActivity** | `panels`, `scenario2panel`, `paneldata` | ScenarioDAO | `savePanel()`, `getPanelsForScenarioID()`, `savePanelData()` |
| **PVGISActivity** | `paneldata` | ScenarioDAO | `savePanelData()` |
| **LoadProfileActivity** | `loadprofile`, `loadprofiledata`, `scenario2loadprofile` | ScenarioDAO | `saveLoadProfile()`, `getLoadProfileForScenarioID()`, `createLoadProfileDataEntries()`, `deleteLoadProfileData()` |
| **EVScheduleActivity** | `evcharge`, `scenario2evcharge` | ScenarioDAO | `saveEVChargeForScenario()`, `getEVChargesForScenarioID()` |
| **EVDivertActivity** | `evdivert`, `scenario2evdivert` | ScenarioDAO | `saveEVDivertForScenario()`, `getEVDivertForScenarioID()` |
| **WaterScheduleActivity** | `hwschedule`, `scenario2hwschedule` | ScenarioDAO | `saveHWScheduleForScenario()`, `getHWSchedulesForScenarioID()` |
| **WaterSettingsActivity** | `hwsystem`, `scenario2hwsystem` | ScenarioDAO | `saveHWSystemForScenario()`, `getHWSystemForScenarioID()` |

### Import Activities (No Direct DB Access)

| Activity | DB Tables | DAO Access | DAO Methods Used |
|----------|-----------|------------|------------------|
| **ImportAlphaActivity** | None (delegates to Workers) | None | None (UI only, triggers Workers) |

### Worker Classes (Direct Repository Access)

| Worker Class | DB Tables | DAO Access | DAO Methods Used |
|--------------|-----------|------------|------------------|
| **CostingWorker** | `scenarios`, `costings`, `scenariosimulationdata`, `PricePlans` | ScenarioDAO, CostingDAO, PricePlanDAO | `getScenarioForID()`, `getSimulationDataForScenario()`, `saveCosting()`, `loadPricePlansNow()`, `getAllDayRatesForPricePlanID()`|
| **SimulationWorker** | `scenarios`, `scenariosimulationdata`, `batteries`, `panels`, `inverters`, `hwsystem`, `loadprofile`, `evcharge`, `hwschedule`, `loadshift`, `discharge2grid`, all `scenario2*` tables | ScenarioDAO | `getAllScenariosThatNeedSimulation()`, `getScenarioComponentsForScenarioID()`, `getSimulationInputNoSolar()`, `getSimulationInputForPanel()`, `saveSimulationDataForScenario()`|
| **GenerateMissingLoadDataWorker** | `loadprofile`, `loadprofiledata` | ScenarioDAO | `checkForMissingLoadProfileData()`, `createLoadProfileDataEntries()`|
| **DeleteLoadDataFromProfileWorker** | `loadprofiledata` | ScenarioDAO | `deleteLoadProfileData()`|
| **AlphaESS ImportWorker** | `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | `addRawEnergy()`, `addRawPower()`, `checkSysSnForDataOnDate()`|
| **AlphaESS ExportWorker** | `alphaESSRawEnergy`, `alphaESSRawPower`, `alphaESSTransformedData` | AlphaEssDAO | `getAlphaESSEnergyForSharing()`, `getAlphaESSPowerForSharing()`, `addTransformedData()`|
| **AlphaESS GenerationWorker** | `alphaESSTransformedData`, `loadprofiledata`, `paneldata`, `scenarios`, `inverters`, `panels`, `batteries`, `loadprofile` | AlphaEssDAO, ScenarioDAO | `getAlphaESSTransformedData()`, `addNewScenarioWithComponents()`, `createLoadProfileDataEntries()`, `savePanelData()`|
| **AlphaESS CatchUpWorker** | `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | `addRawEnergy()`, `addRawPower()`, `getLatestDateForSn()`|
| **AlphaESS DailyWorker** | `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | `addRawEnergy()`, `addRawPower()`|
| **ESBN ImportWorker** | `loadprofiledata`, `scenarios`, `loadprofile`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `createLoadProfileDataEntries()`, `addNewScenarioWithComponents()`, `saveLoadProfile()`, `addTransformedData()` (uses AlphaESS tables)|
| **ESBN CatchUpWorker** | `loadprofiledata`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `createLoadProfileDataEntries()`, `getLatestDateForSn()`, `addTransformedData()` (uses AlphaESS tables)|
| **ESBN GenerationWorker** | `loadprofiledata`, `paneldata`, `scenarios`, `inverters`, `panels`, `batteries`, `loadprofile`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `addNewScenarioWithComponents()`, `createLoadProfileDataEntries()`, `savePanelData()`, `addTransformedData()` (uses AlphaESS tables)|
| **HomeAssistant HACatchupWorker** | `loadprofiledata`, `paneldata`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `createLoadProfileDataEntries()`, `savePanelData()`, `addTransformedData()` (uses AlphaESS tables)|
| **HomeAssistant GenerationWorker** | `loadprofiledata`, `paneldata`, `scenarios`, `inverters`, `panels`, `batteries`, `loadprofile`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `addNewScenarioWithComponents()`, `createLoadProfileDataEntries()`, `savePanelData()`, `getAlphaESSTransformedData()` (uses AlphaESS tables)|
| **PVGISLoader** | `paneldata` | ScenarioDAO | `savePanelData()` |

### Abstract Classes (Base Classes for Workers)

| Abstract Class | DB Tables | DAO Access | DAO Methods Used |
|----------------|-----------|------------|------------------|
| **AbstractGenerationWorker** | `scenarios`, `loadprofile`, `loadprofiledata`, `inverters`, `panels`, `paneldata`, `batteries`, `hwsystem`, `evcharge`, `hwschedule`, `loadshift`, `discharge2grid`, all `scenario2*` tables, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `getScenarios()`, `insertScenarioAndReturnID()`, `saveLoadProfileAndReturnID()`, `createLoadProfileDataEntries()`, `saveInverter()`, `savePanel()`, `savePanelData()`, `getAlphaESSTransformedData()`|

### Data Import Systems

| Import System | DB Tables | DAO Access | DAO Methods Used |
|---------------|-----------|------------|------------------|
| **AlphaESS Import System** | `alphaESSRawEnergy`, `alphaESSRawPower`, `alphaESSTransformedData` | AlphaEssDAO | `addRawEnergy()`, `addRawPower()`, `addTransformedData()`, `checkSysSnForDataOnDate()`, `getExportDatesForSN()`, `clearAlphaESSDataForSN()`|
| **ESBN Import System** | `loadprofiledata`, `scenarios`, `loadprofile`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `createLoadProfileDataEntries()`, `addNewScenarioWithComponents()`, `saveLoadProfile()`, `addTransformedData()` (uses AlphaESS tables)|
| **HomeAssistant Import System** | `loadprofiledata`, `paneldata`, `alphaESSTransformedData` | ScenarioDAO, AlphaEssDAO | `createLoadProfileDataEntries()`, `savePanelData()`, `addTransformedData()`, `getAlphaESSTransformedData()` (uses AlphaESS tables)|

## Database Table Relationships and Architecture

### Architecture Overview

The CompareToUT application uses a dual-path architecture for database access:

1. **UI-driven path**: UI Components → ComparisonUIViewModel → ToutcRepository → DAO → Database
2. **Background processing path**: Worker Classes → ToutcRepository (direct) → DAO → Database

### Data Access Object (DAO) Layer

The application uses **only 4 DAO classes** to access all database tables:

- **ScenarioDAO** - Manages scenarios, components, and all junction table relationships
- **PricePlanDAO** - Handles price plans and day rate structures  
- **CostingDAO** - Manages cost calculation results
- **AlphaEssDAO** - Handles all import data (AlphaESS, ESBN, HomeAssistant via shared tables)

**Important:** All import systems (AlphaESS, ESBN, HomeAssistant) use the same DAO (`AlphaEssDAO`) and table structure. The `alphaESS*` tables serve as a shared format for all energy data imports, regardless of the original source.

### Core Table Categories

**Core Business Tables:**
- `scenarios` - Central scenario definitions with configuration metadata
- `PricePlans` - Electricity tariff structures and pricing rules  
- `DayRates` - Time-of-use rate definitions within price plans
- `costings` - Calculated cost comparison results

**Energy System Components:**
- `batteries` - Battery storage system specifications
- `inverters` - Power conversion equipment definitions
- `panels` - Solar panel array configurations
- `hwsystem` - Hot water system specifications
- `loadprofile` - Electricity consumption pattern definitions
- `evcharge` - Electric vehicle charging profile settings
- `evdivert` - EV surplus energy diversion configurations
- `hwschedule` - Hot water heating schedule definitions
- `hwdivert` - Hot water surplus energy diversion settings
- `loadshift` - Load shifting configuration for demand management
- `discharge2grid` - Grid discharge/export settings

**Association Tables (Junction Tables):**
All many-to-many relationships between scenarios and components:
- `scenario2battery`, `scenario2inverter`, `scenario2panel`
- `scenario2hwsystem`, `scenario2loadprofile`, `scenario2evcharge`
- `scenario2evdivert`, `scenario2hwschedule`, `scenario2hwdivert`
- `scenario2loadshift`, `scenario2discharge`

**Time-Series Data Tables:**
- `loadprofiledata` - High-resolution electricity consumption measurements
- `paneldata` - Solar generation measurements and forecasts
- `scenariosimulationdata` - Simulation results for energy flow modeling

**Import/Integration Tables:**
- `alphaESSRawEnergy` - Raw daily energy data from AlphaESS inverters
- `alphaESSRawPower` - Raw 5-minute power measurements from AlphaESS
- `alphaESSTransformedData` - Processed and normalized energy data

### Data Access Patterns

**DAO Distribution and Key Methods:**

- **ScenarioDAO** (Main DAO - 170+ methods): Handles 25+ tables including scenarios, all components, junction tables, time-series data
  - Component Management: `addNewScenario()`, `addNewInverter()`, `addNewBattery()`, `addNewPanels()`, `addNewHWSystem()`, `addNewLoadProfile()`, etc.
  - Junction Management: `addNewScenario2Inverter()`, `addNewScenario2Battery()`, `addNewScenario2Panel()`, etc.
  - Complex Operations: `addNewScenarioWithComponents()`, `deleteScenario()`, `copyScenario()`
  - Data Retrieval: `getInvertersForScenarioID()`, `getBatteriesForScenarioID()`, `getPanelsForScenarioID()`, etc.
  - Time-Series: `saveSimulationDataForScenario()`, `getSimulationDataForScenario()`, `createLoadProfileDataEntries()`
  - Visualization: `getBarData()`, `getLineData()`, `getMonthlyBarData()`, `getYearBarData()`

- **PricePlanDAO** (30+ methods): Manages `PricePlans` and `DayRates` tables
  - CRUD Operations: `addNewPricePlan()`, `addNewDayRate()`, `updatePricePlan()`, `deletePricePlan()`
  - Complex Operations: `addNewPricePlanWithDayRates()`, `updatePricePlanWithDayRates()`
  - Queries: `loadPricePlans()`, `getAllDayRatesForPricePlanID()`, `getPricePlanID()`

- **CostingDAO** (10+ methods): Manages `costings` table for financial calculations
  - Core Operations: `saveCosting()`, `loadCostings()`, `getBestCostingForScenario()`
  - Maintenance: `deleteRelatedCostings()`, `pruneCostings()`, `costingExists()`
  - Export: `getAllComparisonsNow()`

- **AlphaEssDAO** (50+ methods): Manages all AlphaESS import tables with complex time-based aggregations
  - Data Import: `addRawEnergy()`, `addRawPower()`, `addTransformedData()`
  - Time Aggregations: `sumHour()`, `sumDOY()`, `sumDOW()`, `sumMonth()`, `sumYear()`
  - Averages: `avgHour()`, `avgDOY()`, `avgDOW()`, `avgMonth()`, `avgYear()`
  - Analysis: `getKPIs()`, `getKeyStats()`, `getBaseLoad()`, `getLosses()`
  - System Management: `clearAlphaESSDataForSN()`, `checkSysSnForDataOnDate()`

**Worker Class Repository Usage:**
Worker classes bypass the ViewModel layer and directly instantiate ToutcRepository:
```java
mToutcRepository = new ToutcRepository((Application) context);
```

This pattern is used by:
- Simulation and calculation workers (CostingWorker, SimulationWorker)
- Data import workers (AlphaESS, ESBN, HomeAssistant importers)
- Data processing workers (load profile generators, cleanup workers)

### Data Flow Architecture

```
┌─────────────────┐    ┌──────────────────────┐
│   UI Components │────│ ComparisonUIViewModel │
│ (Activities/    │    │                      │
│  Fragments)     │    │                      │
└─────────────────┘    └─────────┬────────────┘
                                 │
┌─────────────────┐              │
│  Worker Classes │              │
│ (Background     │              │
│  Processing)    │              │
└─────────┬───────┘              │
          │                      │
          └──────┬─────────────────┘
                 │
        ┌────────▼─────────┐
        │  ToutcRepository │
        │                  │
        └────────┬─────────┘
                 │
    ┌────────────┼────────────┬────────────┐
    │            │            │            │
┌───▼────┐ ┌─────▼─────┐ ┌───▼─────┐ ┌────▼─────┐
│Scenario│ │PricePlan  │ │Costing  │ │AlphaEss  │
│   DAO  │ │    DAO    │ │   DAO   │ │   DAO    │
└───┬────┘ └─────┬─────┘ └───┬─────┘ └────┬─────┘
    │            │           │            │
    └────────────┼───────────┼────────────┘
                 │           │
        ┌────────▼───────────▼─────┐
        │     SQLite Database      │
        │      (33 Tables)         │
        └─────────────────────────────┘
```

The dual-path architecture allows for:
- Responsive UI interactions through the ViewModel layer
- Efficient background processing through direct repository access
- Proper separation of concerns while maintaining performance for intensive operations

## Key Implementation Notes

1. **Worker Independence**: Worker classes operate independently of the UI lifecycle and directly access the repository to ensure background operations continue even when UI components are destroyed.

2. **DAO Specialization**: While ScenarioDAO handles the majority of tables, specialized DAOs (PricePlanDAO, CostingDAO, AlphaEssDAO) provide focused interfaces for specific domains.

3. **Table Name Consistency**: Actual table names use mixed case (`PricePlans`, `DayRates`) and lowercase (`scenarios`, `batteries`) as defined in @Entity annotations.

4. **Data Import Patterns**: Three main import systems (AlphaESS, ESBN, HomeAssistant) all follow similar patterns but target different table sets based on data source capabilities.