# Database Dependencies by Activity/Package

This document outlines the database dependencies for each activity and package in the CompareToUT application. The architecture supports two main invocation patterns:

1. **UI Components** → ComparisonUIViewModel → ToutcRepository → DAO classes → Database Tables
2. **Worker Classes** → ToutcRepository (direct) → DAO classes → Database Tables

## Complete Database Tables List

Based on the @Entity annotations, the application uses 33 database tables:

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

## Activity/Package to Database Dependencies

### UI Components (via ComparisonUIViewModel)

| Activity/Package | DB Tables | DAO Access | Invocation Path |
|------------------|-----------|------------|-----------------|
| **Main Comparison** | `scenarios`, `costings` | ScenarioDAO, CostingDAO | UI → ComparisonUIViewModel → ToutcRepository → DAO |
| **Price Plan Management** | `PricePlans`, `DayRates` | PricePlanDAO | UI → ComparisonUIViewModel → ToutcRepository → PricePlanDAO |
| **Scenario Management** | `scenarios`, `scenario2*` (all junction tables) | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Battery Package** | `batteries`, `scenario2battery` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Panel Package** | `panels`, `scenario2panel`, `paneldata` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Inverter Package** | `inverters`, `scenario2inverter` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **EV Package** | `evcharge`, `evdivert`, `scenario2evcharge`, `scenario2evdivert` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | `hwsystem`, `hwschedule`, `hwdivert`, `scenario2hwsystem`, `scenario2hwschedule`, `scenario2hwdivert` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Profile Package** | `loadprofile`, `loadprofiledata`, `scenario2loadprofile` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Shift Package** | `loadshift`, `scenario2loadshift` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Discharge to Grid** | `discharge2grid`, `scenario2discharge` | ScenarioDAO | UI → ComparisonUIViewModel → ToutcRepository → ScenarioDAO |

### Worker Classes (Direct Repository Access)

| Worker Class | DB Tables | DAO Access | Invocation Path |
|--------------|-----------|------------|-----------------|
| **CostingWorker** | `scenarios`, `costings`, `scenariosimulationdata`, `PricePlans` | ScenarioDAO, CostingDAO, PricePlanDAO | CostingWorker → ToutcRepository → DAO |
| **SimulationWorker** | `scenarios`, `scenariosimulationdata`, `batteries`, `panels`, `inverters`, `hwsystem`, `loadprofile`, `evcharge`, `hwschedule`, `loadshift`, `discharge2grid`, all `scenario2*` tables | ScenarioDAO | SimulationWorker → ToutcRepository → ScenarioDAO |
| **GenerateMissingLoadDataWorker** | `loadprofile`, `loadprofiledata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **DeleteLoadDataFromProfileWorker** | `loadprofiledata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **AlphaESS ImportWorker** | `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | Worker → ToutcRepository → AlphaEssDAO |
| **AlphaESS ExportWorker** | `alphaESSRawEnergy`, `alphaESSRawPower`, `alphaESSTransformedData` | AlphaEssDAO | Worker → ToutcRepository → AlphaEssDAO |
| **AlphaESS GenerationWorker** | `alphaESSTransformedData`, `loadprofiledata`, `paneldata` | AlphaEssDAO, ScenarioDAO | Worker → ToutcRepository → DAO |
| **AlphaESS CatchUpWorker** | `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | Worker → ToutcRepository → AlphaEssDAO |
| **AlphaESS DailyWorker** | `alphaESSRawEnergy`, `alphaESSRawPower` | AlphaEssDAO | Worker → ToutcRepository → AlphaEssDAO |
| **ESBN ImportWorker** | `loadprofiledata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **ESBN CatchUpWorker** | `loadprofiledata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **ESBN GenerationWorker** | `loadprofiledata`, `paneldata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **HomeAssistant HACatchupWorker** | `loadprofiledata`, `paneldata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **HomeAssistant GenerationWorker** | `loadprofiledata`, `paneldata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |
| **AbstractGenerationWorker** | `loadprofiledata`, `paneldata` | ScenarioDAO | Worker → ToutcRepository → ScenarioDAO |

## Database Table Relationships and Architecture

### Architecture Overview

The CompareToUT application uses a dual-path architecture for database access:

1. **UI-driven path**: UI Components → ComparisonUIViewModel → ToutcRepository → DAO → Database
2. **Background processing path**: Worker Classes → ToutcRepository (direct) → DAO → Database

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

**DAO Distribution:**
- **ScenarioDAO**: Handles 25+ tables (scenarios, all components, junction tables, time-series data)
- **PricePlanDAO**: Manages `PricePlans` and `DayRates` tables
- **CostingDAO**: Manages `costings` table for financial calculations
- **AlphaEssDAO**: Manages all AlphaESS import tables (`alphaESSRawEnergy`, `alphaESSRawPower`, `alphaESSTransformedData`)

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