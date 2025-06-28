# Database Dependencies by Activity/Package

This document outlines the database dependencies for each activity and package in the CompareToUT application. The architecture follows the pattern: UI Components → ComparisonUIViewModel → ToutcRepository → DAO classes → Database Tables.

## Activity/Package to Database Dependencies

| Activity/Package | DB Table | DAO Method(s) | Invocation Path |
|------------------|----------|---------------|-----------------|
| **Main Comparison** | scenarios | loadScenarios, getScenario, addNewScenario, updateScenario, deleteScenario | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Main Comparison** | costings | loadCostings, saveCosting, getBestPlan | ComparisonUIViewModel → ToutcRepository → CostingDAO |
| **Price Plan Management** | priceplans | loadPricePlans, addNewPricePlan, updatePricePlan, deletePricePlan | ComparisonUIViewModel → ToutcRepository → PricePlanDAO |
| **Price Plan Management** | dayrates | loadPricePlans, addNewDayRate, updateDayRate, deleteDayRatesInPlan | ComparisonUIViewModel → ToutcRepository → PricePlanDAO |
| **Scenario Management** | scenarios | loadScenarios, getScenario, addNewScenario, updateScenario, deleteScenario | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Scenario Management** | scenario2inverter | loadInverterRelations, addNewScenario2Inverter, deleteScenario2Inverter | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Scenario Management** | scenario2battery | loadBatteryRelations, addNewScenario2Battery, deleteScenario2Battery | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Scenario Management** | scenario2panel | loadPanelRelations, addNewScenario2Panel, deleteScenario2Panel | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Scenario Management** | scenario2loadprofile | addNewScenario2LoadProfile, deleteScenario2LoadProfile | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Battery Package** | batteries | addNewBattery, updateBattery, deleteBattery, getBatteriesForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Battery Package** | scenario2battery | loadBatteryRelations, addNewScenario2Battery, deleteScenario2Battery | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Panel Package** | panels | addNewPanels, updatePanels, deletePanels, getPanelsForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Panel Package** | scenario2panel | loadPanelRelations, addNewScenario2Panel, deleteScenario2Panel | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Panel Package** | paneldata | addNewPanelData, updatePanelData, deletePanelData | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Inverter Package** | inverters | addNewInverter, updateInverter, deleteInverter, getInvertersForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Inverter Package** | scenario2inverter | loadInverterRelations, addNewScenario2Inverter, deleteScenario2Inverter | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **EV Package** | evcharge | addNewEVCharge, updateEVCharge, deleteEVCharge, getEVChargesForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **EV Package** | scenario2evcharge | loadEVChargeRelations, addNewScenario2EVCharge, deleteScenario2EVCharge | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **EV Package** | evdivert | addNewEVDivert, updateEVDivert, deleteEVDivert, getEVDivertsForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **EV Package** | scenario2evdivert | loadEVDivertRelations, addNewScenario2EVDivert, deleteScenario2EVDivert | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | hwsystem | addNewHWSystem, updateHWSystem, deleteHWSystem, getHWSystemForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | scenario2hwsystem | loadHWSystemRelations, addNewScenario2HWSystem, deleteScenario2HWSystem | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | hwschedule | addNewHWSchedule, updateHWSchedule, deleteHWSchedule, getHWSchedulesForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | scenario2hwschedule | loadHWScheduleRelations, addNewScenario2HWSchedule, deleteScenario2HWSchedule | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | hwdivert | addNewHWDivert, updateHWDivert, deleteHWDivert, getHWDivertsForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Water Package** | scenario2hwdivert | addNewScenario2HWDivert, deleteScenario2HWDivert | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Profile Package** | loadprofile | addNewLoadProfile, updateLoadProfile, deleteLoadProfile, getLoadProfileForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Profile Package** | scenario2loadprofile | addNewScenario2LoadProfile, deleteScenario2LoadProfile | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Profile Package** | loadprofiledata | addNewLoadProfileData, updateLoadProfileData, deleteLoadProfileData | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Shift** | loadshift | addNewLoadShift, updateLoadShift, deleteLoadShift, getLoadShiftsForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Load Shift** | scenario2loadshift | loadLoadShiftRelations, addNewScenario2LoadShift, deleteScenario2LoadShift | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Discharge to Grid** | discharge2grid | addNewDischarge, updateDischarge, deleteDischarge, getDischargesForScenarioID | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Discharge to Grid** | scenario2discharge | loadDischargeRelations, addNewScenario2Discharge, deleteScenario2Discharge | ComparisonUIViewModel → ToutcRepository → ScenarioDAO |
| **Data Import (AlphaESS)** | alphaessrawenergy | addRawEnergy, deleteBySystemSerialNum | ComparisonUIViewModel → ToutcRepository → AlphaEssDAO |
| **Data Import (AlphaESS)** | alphaessrawpower | addRawPower, deleteBySystemSerialNum | ComparisonUIViewModel → ToutcRepository → AlphaEssDAO |
| **Data Import (AlphaESS)** | alphaessts5minutedata | addTransformedData, deleteBySystemSerialNum | ComparisonUIViewModel → ToutcRepository → AlphaEssDAO |
| **Cost Analysis** | costings | saveCosting, getBestPlan, deleteRelatedCostings | ComparisonUIViewModel → ToutcRepository → CostingDAO |

## Database Table Relationships

### Core Tables
- **scenarios**: Main scenario definitions
- **priceplans**: Electricity tariff definitions
- **dayrates**: Time-based rates within price plans
- **costings**: Cost calculation results

### Component Tables
- **inverters**: Power conversion equipment
- **batteries**: Energy storage systems
- **panels**: Solar panel arrays
- **hwsystem**: Hot water systems
- **loadprofile**: Electricity consumption patterns

### Junction Tables (Many-to-Many Relationships)
- **scenario2inverter**: Links scenarios to inverters
- **scenario2battery**: Links scenarios to batteries
- **scenario2panel**: Links scenarios to panels
- **scenario2hwsystem**: Links scenarios to hot water systems
- **scenario2loadprofile**: Links scenarios to load profiles
- **scenario2evcharge**: Links scenarios to EV charging profiles
- **scenario2evdivert**: Links scenarios to EV diversion settings
- **scenario2hwschedule**: Links scenarios to hot water schedules
- **scenario2hwdivert**: Links scenarios to hot water diversion
- **scenario2loadshift**: Links scenarios to load shifting
- **scenario2discharge**: Links scenarios to grid discharge settings

### Import/Analysis Tables
- **alphaessrawenergy**: Raw daily energy data from AlphaESS
- **alphaessrawpower**: Raw 5-minute power data from AlphaESS
- **alphaessts5minutedata**: Transformed energy data for analysis
- **loadprofiledata**: Detailed load profile measurements
- **paneldata**: Solar panel generation data

## Data Flow Architecture

```
UI Components (Fragments/Activities)
       ↓
ComparisonUIViewModel
       ↓
ToutcRepository
       ↓
DAO Classes (ScenarioDAO, PricePlanDAO, AlphaEssDAO, CostingDAO)
       ↓
Room Database (ToutcDB)
       ↓
SQLite Database Tables
```

The system uses Android's Room persistence library with a clean architecture pattern, ensuring separation of concerns and maintainable data access patterns.