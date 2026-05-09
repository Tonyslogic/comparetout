# **📘 DESIGN SPECIFICATION: UI2 Architecture & Implementation**

## **1. Architecture & Scope**
This document outlines the technical design for implementing the "UI2" requirements detailed in `plans/UI2/requirement.md`. 
The primary constraints are:
1. **Parallel Implementation:** The new UI (UI2) must run alongside the old UI. The existing `MainActivity` and legacy screens must continue to function perfectly.
2. **Zero Backend Modifications:** No changes are permitted to `ToutcRepository`, Room DAOs, or Entities. All new UI layers must adapt to the existing repository methods.
3. **Pattern:** MVVM (Model-View-ViewModel) using Android Navigation Component, Fragments, and existing `LiveData` to maintain consistency with the current application structure.

---

## **2. Entry Point & Legacy Integration**

### **2.1 `UI2MainActivity`**
A new Activity, `UI2MainActivity`, will be the host for the new user interface.
- **Layout (`activity_ui2_main.xml`):** Contains a `FragmentContainerView` (for the NavHost) and a `BottomNavigationView`.
- **Manifest:** Declared alongside `MainActivity`.
- **Theme:** Uses the existing `@style/Theme.comparetout`.

### **2.2 Toggling UIs**
- **Legacy to UI2:** Add a menu item to `MainActivity`'s options menu: `"Switch to New Experience"`, which launches `UI2MainActivity` and finishes the current activity.
- **UI2 to Legacy:** Add a global menu item in `UI2MainActivity`: `"Switch to Legacy UI"`, returning the user to `MainActivity`.
- **Default Preference:** A `DataStore` or `SharedPreferences` boolean `use_ui2` will dictate which Activity is launched by a lightweight routing Activity (or inside `MainActivity#onCreate`), ensuring the user's preference persists across restarts.

---

## **3. Navigation Architecture**
We will utilize the **Android Jetpack Navigation Component** (`res/navigation/nav_ui2.xml`).

### **3.1 Bottom Navigation Targets**
The `BottomNavigationView` maps to three top-level fragments:
1. `UI2SimulationsFragment`
2. `UI2ComparisonsFragment`
3. `UI2DirectorsFragment`

*Note: The **Dashboard** is not a permanent bottom tab, but rather the default view populated when an active simulation is set.*

### **3.2 Shared State (Active Simulation)**
A scoped `UI2SharedViewModel` (tied to `UI2MainActivity`) will hold the `activeSimulationId` (Long). 
- When a user selects a simulation from `UI2SimulationsFragment`, `UI2SharedViewModel.setActiveSimulationId(id)` is called, and the NavController navigates to `UI2DashboardFragment`.

---

## **4. Screen Specifications & Data Mapping**

### **4.1 Dashboard (`UI2DashboardFragment`)**
**Role:** Displays the details, summaries, and graphs of the currently active simulation.
**ViewModel:** `UI2DashboardViewModel`
- Observes `activeSimulationId` from `UI2SharedViewModel`.
- Fetches `ScenarioComponents` via `ToutcRepository.getScenarioComponentsForScenarioID(id)`.
- Fetches `Costings` and KPI data via `ToutcRepository.getBestCostingForScenario(id)` and `getSimKPIsForScenario(id)`.
**UI Components:**
- Header with dropdown (populated by `ToutcRepository.getAllScenarios()`).
- Summary section featuring a Sankey chart (implemented via a custom View or integrating an external charting library compatible with the existing `SimulationInputData`).
- Accordion Layouts (RecyclerView with expandable items) mapping to `Usage Data`, `Tariff Plan`, `PV System`, `Battery`, `EV`, `Heat Pump`, `Hot Water`.

### **4.2 Simulations Tab (`UI2SimulationsFragment`)**
**Role:** Central hub to view, edit, delete, and add scenarios.
**ViewModel:** `UI2SimulationsViewModel`
- Observes `ToutcRepository.getAllScenarios()`.
- Provides delete actions mapping to `ToutcRepository.deleteScenario(int)` and `deleteRelatedCostings(int)`.
**UI Components:**
- `RecyclerView` of Simulation Cards displaying `Costings.annualCost` and scenario name.
- Floating Action Button to launch `UI2WizardActivity`.

### **4.3 Comparisons Tab (`UI2ComparisonsFragment`)**
**Role:** Multi-simulation or Multi-plan comparisons.
**ViewModel:** `UI2ComparisonsViewModel`
- Uses `ToutcRepository.getAllCostings()` and `getAllScenarios()` to build comparison matrices.
**UI Components:**
- Top `TabLayout` or Toggle: "Compare Simulations" vs. "Compare Plans".
- `RecyclerView` tables mapping attributes across selected scenarios.
- **Graph Pane:** Reuses the existing `ComparisonFragment` charting UI and logic as-is. Includes the current implementation's filters, time duration, and steps. Building custom graph UI elements specific to UI2 is deferred to a separate requirement.

### **4.4 Directors Tab (`UI2DirectorsFragment`)**
**Role:** Manage reusable components (Tariffs, Usage Profiles, System Elements).
**ViewModel:** `UI2DirectorsViewModel`
- Maps to `ToutcRepository.getAllPricePlans()`, `ToutcRepository.getLinkedLoadProfiles()`, `getAllInverterRelations()`, etc.
**UI Components:**
- Categorized list of assets.
- **Visualization:** "View Graph" actions for Usage Data Sources and System Profiles, which launch the existing graph/visualization fragments to preview the data, deferring any new custom graph controls.
- Modals for **Link/Copy** actions. These directly wire into the existing backend functions:
  - `ToutcRepository.copyInverterFromScenario(source, target)`
  - `ToutcRepository.linkInverterFromScenario(source, target)`
  - `ToutcRepository.linkLoadProfileFromScenario(source, target)`
  - `ToutcRepository.copyLoadProfileFromScenario(source, target)`

### **4.5 Global Menu**
Accessed via a `Toolbar` in `UI2MainActivity`. 
- Settings map to existing `DataStore` keys (currency, timezone, theme).

---

## **5. Wizard Architecture (`UI2WizardActivity`)**
For creating/editing a Simulation smoothly without losing context.
**Structure:** A dedicated `UI2WizardActivity` hosting a `ViewPager2` or its own `NavHostFragment` (`wizard_graph.xml`) for sequential steps.
**ViewModel:** `UI2WizardViewModel` (Activity scoped).
- Maintains a temporary `ScenarioComponents` builder object.
**Steps:**
1. **Setup:** Select start type (scratch, copy, linked). Maps to `ToutcRepository.copyScenario()`.
2. **Location & Data Source:** Captures basic properties.
3. **Usage Data:** Interface to launch importer activities or link existing `LoadProfile`.
4. **System Components:** Adds `Inverter`, `Panel`, `Battery`, etc., using temporary domain objects.
5. **Tariff Plan:** Fetches `ToutcRepository.getAllPricePlans()`.
6. **Summary:** Submits to database via `ToutcRepository.insertScenarioAndReturnID()` or `updateScenario()`. Triggers a background simulation worker, same as legacy UI.

---

## **6. Backend Continuity Assurances**
- **Data Integrity:** UI2 uses exact same `ToutcRepository` singleton from `TOUTCApplication`.
- **Database Notifications:** Because Room `LiveData` flows directly from SQLite into the ViewModels, edits made in UI2 will instantly be reflected if the user switches to Legacy UI, and vice versa.
- **Workers:** The existing `CostingWorker` and `SimulationWorker` triggers will be identical (typically fired via `observerSimulationWorker()` pattern when a scenario is saved).

---

## **7. Recommended Next Steps for Implementation**
1. Create `UI2MainActivity` and `nav_ui2.xml` with placeholder fragments.
2. Implement the `use_ui2` preference toggle.
3. Build out `UI2SharedViewModel` and `UI2DashboardFragment` to read existing simulations.
4. Construct the `UI2WizardActivity` step-by-step.
5. Wire up the Directors tab link/copy operations.
