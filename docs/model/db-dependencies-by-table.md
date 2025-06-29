# Database Table Dependencies - Impact Analysis

This document provides a reverse mapping showing which packages, activities, fragments, and workers depend on each database table. This helps maintainers understand the impact of modifying any database table structure.

## How to Use This Document

When modifying a database table:
1. Find the table in the list below
2. Review all the dependent classes/packages listed
3. Consider the impact on each dependent component
4. Test all affected areas after making changes

## Database Table Dependencies

### Core Business Tables

#### `scenarios`
**Components that depend on this table:**
- **Activities**: MainActivity, ScenarioActivity, BatterySettingsActivity, BatteryChargingActivity, BatteryDischargeActivity, InverterActivity, PanelActivity, PVGISActivity, LoadProfileActivity, EVScheduleActivity, EVDivertActivity, WaterScheduleActivity, WaterSettingsActivity
- **Fragments**: ComparisonFragment, ScenarioNavFragment, BatterySettingsFragment, BatteryChargingFragment, BatteryDischargeFragment, PanelFragment, InverterFragment, EVScheduleFragment, EVDivertFragment, WaterScheduleFragment, LoadProfilePropertiesFragment, LoadProfileDailyDistributionFragment, LoadProfileHourlyDistributionFragment, LoadProfileMonthlyDistributionFragment, ImportGenerateScenarioFragment, ScenarioDetails, ScenarioGraphs, ScenarioOverview, ScenarioMonthly, ScenarioYear, ImportAlphaGenerateScenario, ImportESBNGenerateScenario, ImportHAGenerateScenario, CompareScenarioSelectDialog, ScenarioSelectDialog
- **Workers**: CostingWorker, SimulationWorker, AlphaESS GenerationWorker, ESBN ImportWorker, ESBN GenerationWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `PricePlans`
**Components that depend on this table:**
- **Activities**: MainActivity, PricePlanActivity
- **Fragments**: PricePlanNavFragment, PricePlanEditFragment, ImportOverviewFragment
- **Workers**: CostingWorker

#### `DayRates`
**Components that depend on this table:**
- **Activities**: PricePlanActivity
- **Fragments**: PricePlanEditFragment, PricePlanEditDayFragment, ImportOverviewFragment

#### `costings`
**Components that depend on this table:**
- **Activities**: MainActivity
- **Fragments**: ComparisonFragment, ScenarioDetails, ScenarioOverview
- **Workers**: CostingWorker

### Energy System Component Tables

#### `batteries`
**Components that depend on this table:**
- **Activities**: BatterySettingsActivity, BatteryChargingActivity, BatteryDischargeActivity
- **Fragments**: BatterySettingsFragment, BatteryChargingFragment, BatteryDischargeFragment
- **Workers**: SimulationWorker, AlphaESS GenerationWorker, ESBN GenerationWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `inverters`
**Components that depend on this table:**
- **Activities**: InverterActivity
- **Fragments**: InverterFragment
- **Workers**: SimulationWorker, AlphaESS GenerationWorker, ESBN GenerationWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `panels`
**Components that depend on this table:**
- **Activities**: PanelActivity
- **Fragments**: PanelFragment
- **Workers**: SimulationWorker, AlphaESS GenerationWorker, ESBN GenerationWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `hwsystem`
**Components that depend on this table:**
- **Activities**: WaterSettingsActivity
- **Fragments**: WaterScheduleFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `loadprofile`
**Components that depend on this table:**
- **Activities**: LoadProfileActivity
- **Fragments**: LoadProfilePropertiesFragment, LoadProfileDailyDistributionFragment, LoadProfileHourlyDistributionFragment, LoadProfileMonthlyDistributionFragment
- **Workers**: SimulationWorker, GenerateMissingLoadDataWorker, AlphaESS GenerationWorker, ESBN ImportWorker, ESBN GenerationWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `evcharge`
**Components that depend on this table:**
- **Activities**: EVScheduleActivity
- **Fragments**: EVScheduleFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `evdivert`
**Components that depend on this table:**
- **Activities**: EVDivertActivity
- **Fragments**: EVDivertFragment, BatteryDischargeFragment
- **Workers**: SimulationWorker

#### `hwschedule`
**Components that depend on this table:**
- **Activities**: WaterScheduleActivity
- **Fragments**: WaterScheduleFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `hwdivert`
**Components that depend on this table:**
- **Workers**: SimulationWorker

#### `loadshift`
**Components that depend on this table:**
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `discharge2grid`
**Components that depend on this table:**
- **Fragments**: BatteryDischargeFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

### Junction Tables (Many-to-Many Relationships)

#### `scenario2battery`
**Components that depend on this table:**
- **Activities**: BatterySettingsActivity, BatteryChargingActivity, BatteryDischargeActivity
- **Fragments**: BatterySettingsFragment, BatteryChargingFragment, BatteryDischargeFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2inverter`
**Components that depend on this table:**
- **Activities**: InverterActivity
- **Fragments**: InverterFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2panel`
**Components that depend on this table:**
- **Activities**: PanelActivity
- **Fragments**: PanelFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2hwsystem`
**Components that depend on this table:**
- **Activities**: WaterSettingsActivity
- **Fragments**: WaterScheduleFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2loadprofile`
**Components that depend on this table:**
- **Activities**: LoadProfileActivity
- **Fragments**: LoadProfilePropertiesFragment, LoadProfileDailyDistributionFragment, LoadProfileHourlyDistributionFragment, LoadProfileMonthlyDistributionFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2evcharge`
**Components that depend on this table:**
- **Activities**: EVScheduleActivity
- **Fragments**: EVScheduleFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2evdivert`
**Components that depend on this table:**
- **Activities**: EVDivertActivity
- **Fragments**: EVDivertFragment, BatteryDischargeFragment
- **Workers**: SimulationWorker

#### `scenario2hwschedule`
**Components that depend on this table:**
- **Activities**: WaterScheduleActivity
- **Fragments**: WaterScheduleFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2hwdivert`
**Components that depend on this table:**
- **Workers**: SimulationWorker

#### `scenario2loadshift`
**Components that depend on this table:**
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `scenario2discharge`
**Components that depend on this table:**
- **Fragments**: BatteryDischargeFragment
- **Workers**: SimulationWorker
- **Abstract Classes**: AbstractGenerationWorker

### Time-Series Data Tables

#### `loadprofiledata`
**Components that depend on this table:**
- **Fragments**: LoadProfileDailyDistributionFragment, LoadProfileHourlyDistributionFragment, LoadProfileMonthlyDistributionFragment
- **Workers**: SimulationWorker, GenerateMissingLoadDataWorker, DeleteLoadDataFromProfileWorker, AlphaESS GenerationWorker, ESBN ImportWorker, ESBN CatchUpWorker, ESBN GenerationWorker, HomeAssistant HACatchupWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

#### `paneldata`
**Components that depend on this table:**
- **Activities**: PVGISActivity
- **Fragments**: PanelFragment
- **Workers**: SimulationWorker, AlphaESS GenerationWorker, ESBN GenerationWorker, HomeAssistant HACatchupWorker, HomeAssistant GenerationWorker, PVGISLoader
- **Abstract Classes**: AbstractGenerationWorker

#### `scenariosimulationdata`
**Components that depend on this table:**
- **Fragments**: BaseGraphsFragment, ScenarioDetails, ScenarioGraphs, ScenarioMonthly, ScenarioYear
- **Workers**: CostingWorker, SimulationWorker

### Import/Integration Tables

#### `alphaESSRawEnergy`
**Components that depend on this table:**
- **Fragments**: ImportKeyStatsFragment, BaseGraphsFragment, ImportAlphaOverview, ImportAlphaKeyStats, ImportESBNOverview, ImportHAOverview
- **Workers**: AlphaESS ImportWorker, AlphaESS ExportWorker, AlphaESS CatchUpWorker, AlphaESS DailyWorker

#### `alphaESSRawPower`
**Components that depend on this table:**
- **Fragments**: ImportKeyStatsFragment, BaseGraphsFragment
- **Workers**: AlphaESS ImportWorker, AlphaESS ExportWorker, AlphaESS CatchUpWorker, AlphaESS DailyWorker

#### `alphaESSTransformedData`
**Components that depend on this table:**
- **Fragments**: ImportKeyStatsFragment, BaseGraphsFragment, ScenarioGraphs, ImportAlphaOverview, ImportAlphaKeyStats, ImportAlphaGraphs, ImportAlphaGenerateScenario, ImportESBNOverview, ImportESBNGraphs, ImportESBNGenerateScenario, ImportHAOverview, ImportHAKeyStats, ImportHAGraphs, ImportHAGenerateScenario
- **Workers**: AlphaESS ExportWorker, AlphaESS GenerationWorker, ESBN ImportWorker, ESBN CatchUpWorker, ESBN GenerationWorker, HomeAssistant HACatchupWorker, HomeAssistant GenerationWorker
- **Abstract Classes**: AbstractGenerationWorker

## High-Impact Tables

The following tables have the most dependencies and should be modified with extreme caution:

### Highest Impact (20+ dependencies)
- **`scenarios`** - Core table used by almost all components
- **`loadprofiledata`** - Critical for load profile management and import systems
- **`alphaESSTransformedData`** - Central to all import and analysis functionality

### High Impact (10-19 dependencies)
- **`paneldata`** - Solar panel data used across generation and simulation
- **`scenariosimulationdata`** - Simulation results used by analysis and graphing
- **`batteries`**, **`inverters`**, **`panels`** - Core component tables
- **`scenario2*` junction tables** - Critical for scenario component relationships

### Medium Impact (5-9 dependencies)
- **`PricePlans`**, **`DayRates`** - Price management system
- **`costings`** - Financial calculation results
- **`loadprofile`** - Load profile definitions
- **`evcharge`**, **`hwsystem`**, **`hwschedule`** - Specific component systems

### Lower Impact (1-4 dependencies)
- **`evdivert`**, **`hwdivert`**, **`loadshift`**, **`discharge2grid`** - Specialized features
- **`alphaESSRawEnergy`**, **`alphaESSRawPower`** - Raw import data

## DAO Impact Summary

When modifying database tables, consider which DAO handles the table:

- **ScenarioDAO modifications affect**: 25+ tables and 150+ components (highest impact)
- **AlphaEssDAO modifications affect**: 3 import tables and 30+ import-related components
- **PricePlanDAO modifications affect**: 2 tables and price management system
- **CostingDAO modifications affect**: 1 table and financial calculation system

## Testing Recommendations

When modifying any database table:

1. **High-impact tables**: Run full integration tests covering all dependent components
2. **Component tables**: Test all related activities, fragments, and worker classes
3. **Junction tables**: Test scenario copying, linking, and component management
4. **Time-series tables**: Test data import, simulation, and visualization components
5. **Import tables**: Test all three import systems (AlphaESS, ESBN, HomeAssistant)

Always verify that database migrations maintain data integrity and that all dependent components continue to function correctly after table modifications.