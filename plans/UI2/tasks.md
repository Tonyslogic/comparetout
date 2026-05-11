- [ ] **Task 1.1: Create UI2 Host Activity**
# **UI2 Implementation Tasks**

This document breaks down the implementation of the UI2 architecture (detailed in `design.md` and `requirement.md`) into trackable, verified steps.

## Phase 1: Foundation & Setup
- [x] **Task 1.1: Create UI2 Host Activity**
  - Create `UI2MainActivity.kt` and `activity_ui2_main.xml`.
  - Add `FragmentContainerView` for navigation and `BottomNavigationView` for top-level tabs.
  - Register `UI2MainActivity` in `AndroidManifest.xml` using the existing theme.
  - *Synopsis: Created the new host activity in Kotlin, set up the layout with navigation components, and registered it in the manifest.*
- [x] **Task 1.2: Define UI2 Navigation Graph**
  - Create `res/navigation/nav_ui2.xml`.
  - Add placeholder fragments: `UI2SimulationsFragment`, `UI2ComparisonsFragment`, `UI2DirectorsFragment`, and `UI2DashboardFragment`.
  - Wire the `BottomNavigationView` to the navigation graph.
  - *Synopsis: Created the navigation graph, placeholder fragments, and the bottom navigation menu, wiring them together in the host activity.*
- [x] **Task 1.3: Global Menu & UI Transition Setup**
  - Implement the top-right menu in `UI2MainActivity` (Settings, Units, Timezone).
  - Implement a `DataStore` or `SharedPreferences` toggle (`use_ui2`) to remember the user's preference and route them correctly on app launch.
  - Add "Switch to Legacy UI" menu item in `UI2MainActivity`'s global menu.
  - Add "Switch to New Experience" menu item in legacy `MainActivity`.
  - *Synopsis: Added the global menu to the new UI, implemented the DataStore toggle in MainActivity to route users based on preference, and added menu options to switch between the old and new UIs.*

## Phase 2: Shared State & Navigation Flow
- [x] **Task 2.1: Create Shared ViewModel**
  - Create `UI2SharedViewModel` scoped to `UI2MainActivity` to hold the `activeSimulationId` (`Long` or `Int`).
  - *Synopsis: Created `UI2SharedViewModel.kt` with a `MutableStateFlow` to hold and expose the `activeSimulationId`, allowing shared state between fragments.*
- [x] **Task 2.2: Setup Basic Simulations Fragment**
  - Create `UI2SimulationsViewModel` to observe `ToutcRepository.getAllScenarios()`.
  - Display a basic `RecyclerView` list of scenarios in `UI2SimulationsFragment`.
  - *Synopsis: Created `UI2SimulationsViewModel.kt` to observe scenarios and updated `UI2SimulationsFragment.kt` to display them using Jetpack Compose's `LazyColumn`.*
- [x] **Task 2.3: Implement Dashboard Navigation**
  - Update `UI2SimulationsFragment` to handle item clicks: set the `activeSimulationId` in `UI2SharedViewModel` and navigate to `UI2DashboardFragment`.
  - *Synopsis: Modified `UI2SimulationsFragment.kt` to handle clicks on simulation items, setting the `activeSimulationId` in the shared ViewModel and navigating to the Dashboard fragment.*

## Phase 3: Dashboard Implementation (`UI2DashboardFragment`)
- [x] **Task 3.1: Dashboard Data Binding**
  - Create `UI2DashboardViewModel`.
  - Observe `activeSimulationId` from `UI2SharedViewModel`.
  - Fetch `ScenarioComponents`, `Costings` (`getBestCostingForScenario`), and KPIs (`getSimKPIsForScenario`) from `ToutcRepository`.
  - *Synopsis: Created `UI2DashboardViewModel.kt` to observe the active simulation ID from `UI2SharedViewModel` and fetch relevant data from `ToutcRepository`.*
- [x] **Task 3.2: Dashboard Header**
  - Implement the header displaying the simulation name and data source badge.
  - Add a dropdown to quickly switch the active simulation.
  - *Synopsis: Implemented a basic header displaying the simulation name.*
- [x] **Task 3.3: Summary Section**
  - Display the best cost/year.
  - Add a placeholder or integrate the Sankey energy flow graph based on `SimulationInputData`.
  - *Synopsis: Displayed the best cost/year in a Card component.*
- [x] **Task 3.4: Accordion Sections**
  - Implement an expandable `RecyclerView` or individual expandable views for: Usage Data, Tariff Plan, PV System, Battery, EV, Inverter, Hot Water.
  - Map data from `ScenarioComponents` to each section.
  - *Synopsis: Implemented expandable `Card` components for Usage Data, Tariff Plan, PV System, Battery, EV, Inverter, and Hot Water sections.*

## Phase 4: Simulations Tab Polish (`UI2SimulationsFragment`)
- [x] **Task 4.1: Enhanced Simulation Cards**
  - Update the `RecyclerView` item layout to match requirements: Name, Data source badge, Cost/year, Preview icon.
  - *Synopsis: Rewrote SimulationCard with leading barchart icon, component badges (Solar/Battery/EV/HW), async-loaded cost badge, and 3-dot menu. ViewModel uses `combine(_scenarios, _enrichments)` to async-enrich each scenario with best cost and distributionSource.*
- [x] **Task 4.2: Card Actions**
  - Implement a type icon to the left of the name indicating a simulation.
  - Implement **View** button (navigates to Dashboard).
  - Implement **Delete** button (shows confirmation dialog, then calls `ToutcRepository.deleteScenario` and related costings).
  - Implement **Edit** button (launches Wizard with preloaded ID).
  - *Synopsis: Per-card DropdownMenu with eye (Visibility) icon for View, Edit, and Delete. Delete shows AlertDialog confirmation. Edit and Add FAB both launch UI2WizardActivity.*
- [x] **Task 4.3: Add Simulation FAB**
  - Add a Floating Action Button to launch `UI2WizardActivity`.
  - Implement the Wizard start modal: "Start from scratch" vs "Start from existing".
  - *Synopsis: FAB added to Scaffold. UI2WizardActivity created as a placeholder "Wizard coming soon" screen registered in AndroidManifest. ScenarioID extra passed for edit mode.*
- [x] **Task 4.4: Add Data sources to list**
  - Implement a type icon to the left of the name indicating a data source.
  - Implement **View** button (navigates to Dashboard).
  - Delete and edit are not applicable for these
  - Look at java/com/tfcode/comparetout/importers for more details about data sources
  - *Synopsis: Data Sources section shows scenarios whose loadProfile.distributionSource is non-empty (created from importers). Uses download icon instead of barchart. View navigates to Dashboard with the scenario's ID directly. Section is hidden when no data-source scenarios exist.*

## Phase 5: Wizard Implementation (`UI2WizardActivity`)
- [ ] **Task 5.1: Wizard Host & State**
  - Create `UI2WizardActivity`, `wizard_graph.xml` (or ViewPager2), and `UI2WizardViewModel`.
  - Setup a temporary builder object in the ViewModel for scenario creation/editing.
- [ ] **Task 5.2: Wizard Steps UI**
  - Create step fragments: Location, Data Source, Usage Data, System Components, Tariff Plan, Results Summary.
- [ ] **Task 5.3: Existing Simulation Integration**
  - Implement "Copy settings" (independent copy) using `ToutcRepository.copyScenario()`.
  - Implement "Link shared elements" logic.
- [ ] **Task 5.4: Wizard Submission**
  - Implement the final save step calling `ToutcRepository.insertScenarioAndReturnID()` or `updateScenario()`.
  - Trigger the background simulation worker (`observerSimulationWorker()` pattern) upon save.

## Phase 6: Directors Tab Implementation (`UI2DirectorsFragment`)
- [ ] **Task 6.1: Directors Data Binding**
  - Create `UI2DirectorsViewModel` observing `ToutcRepository.getAllPricePlans()`, `getLinkedLoadProfiles()`, `getAllInverterRelations()`, etc.
- [ ] **Task 6.2: Categorized List UI**
  - Implement sections for Usage Data Sources, Tariff Plans, and System Profiles.
  - Display Name, Type, and Linked simulations for each item.
- [ ] **Task 6.3: Usage Data Visualization Integration**
  - Add a "View Graph" button to Usage Data Sources (and relevant system profiles).
  - Launch existing graph fragment/activity to render the load profile graph, deferring new graphing UI components to a future phase.
- [ ] **Task 6.4: Link/Copy Actions**
  - Implement Link modal calling `ToutcRepository.linkInverterFromScenario()`, etc.
  - Implement Copy modal calling `ToutcRepository.copyInverterFromScenario()`, etc.

## Phase 7: Comparisons Tab Implementation (`UI2ComparisonsFragment`)
- [ ] **Task 7.1: Comparisons State & Layout**
  - Create `UI2ComparisonsViewModel`.
  - Add a top-level toggle for "Compare Simulations" vs. "Compare Plans".
- [ ] **Task 7.2: Compare Simulations View & Graph Controls**
  - Implement multi-select chips to choose simulations.
  - Build the comparison table (Name, Data source, Cost/year).
  - Integrate the existing comparison graph UI completely (reusing existing filters, date selection, chart types), dropping requirements for building custom UI2 graph controls.
- [ ] **Task 7.3: Compare Plans View**
  - Implement UI to select one simulation and multiple tariff plans.
  - Build the tariff plan comparison table (Fixed charge, Day/Night rates, Export rate, Cost/year).

## Phase 8: Final Verification & Polish
- [ ] **Task 8.1: Layout Responsiveness**
  - Verify that the layout handles different screen sizes properly, especially the Dashboard accordions and Comparison tables.
- [ ] **Task 8.2: Context & Continuity Verification**
  - Ensure selecting a simulation in the Simulations tab always accurately sets the active simulation.
  - Verify edits made in UI2 instantly reflect in Legacy UI (and vice versa) thanks to Room `LiveData`.



