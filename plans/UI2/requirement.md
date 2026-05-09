Absolutely, Tony — here is a **clean, structured, developer‑ready requirement** you can hand directly to a Gemini skill (or any UI‑building agent). It captures the full navigation model, the simulation workflow, the directors system, and the comparison flows exactly as we refined them.

I’ve written it as a **formal product/UI requirement**, not a narrative, so it can be executed against.

---

# **📘 PRODUCT REQUIREMENT: Solar Simulation App UI Architecture**

## **1. Overview**
The application provides:
- A **Dashboard** showing the currently active simulation.
- A **Simulations tab** for managing all simulations.
- A **Comparisons tab** for comparing simulations or tariff plans.
- A **Directors tab** for managing reusable elements (usage data sources, tariff plans, system profiles).
- A **Global Menu** for app‑wide settings (units, timezone, global tariff plan library).

The UI must preserve **simulation context** at all times. Selecting a simulation updates the Dashboard to reflect that simulation.

---

# **2. Global Navigation**

## **2.1 Bottom Navigation Bar**
Persistent across the app:
- **Simulations** (default tab)
- **Comparisons**
- **Directors**

Selecting a tab switches to that view without losing the active simulation context.

## **2.2 Global Menu**
Accessible via top‑right icon.
Contains:
- Units (kWh, €/year, etc.)
- Timezone
- Global tariff plan library (edit/add/delete)
- App settings (theme, language, export defaults)

---

# **3. Simulations Tab**

## **3.1 Layout**
- Title: **“Simulations”**
- Button: **“Add Simulation”** (launches wizard)
- List of simulation cards

## **3.2 Simulation Card Structure**
Each card displays:
- Simulation name (e.g., “PV + Battery”)
- Data source badge (Supplier Sync / Inverter Sync / National Average)
- Cost/year
- Optional preview icon (Sankey or graph thumbnail)
- Buttons:
    - **View** → sets simulation as active and navigates to Dashboard
    - **Edit** → opens wizard preloaded with this simulation’s settings
    - **Delete** → confirmation dialog

## **3.3 Add Simulation (Wizard Launch)**
Wizard start modal:
- **Start from scratch**
- **Start from existing simulation**
    - User selects a simulation
    - Options:
        - **Copy settings** (independent)
        - **Link shared elements** (usage data, tariff plan, system profiles)

---

# **4. Dashboard (Active Simulation View)**

## **4.1 Header**
- Simulation name
- Data source badge
- Dropdown to switch simulations
- Inline buttons:
    - **Compare**
    - **Directors**

## **4.2 Summary Section**
- Best cost/year
- Sankey energy flow graph
- Button: **“View Detailed Graphs”**

## **4.3 Accordion Sections**
Each expandable:
- Usage Data Source
- Tariff Plan
- PV System
- Battery
- EV
- Heat Pump
- Hot Water
- System Profiles (if linked)

Each section shows:
- Summary values
- Link to Directors for editing shared elements

---

# **5. Comparisons Tab**

## **5.1 Comparison Hub**
User chooses:
- **Compare Simulations** (multiple simulations, same or different data sources)
- **Compare Plans** (multiple tariff plans applied to one simulation)

## **5.2 Compare Simulations View**
- Simulation selector (multi‑select chips)
- Table:
    - Simulation name
    - Data source
    - Cost/year
- Graph panel:
    - Load, PV, Feed, Buy
    - Overlay or side‑by‑side mode
- Bottom controls:
    - Duration (Day/Week/Month/Year)
    - Granularity (Hourly/Daily/Monthly)
    - Default: Hourly sum

## **5.3 Compare Plans View**
- Select simulation
- List of tariff plans
- Table:
    - Fixed charge
    - Day/Night rates
    - Export rate
    - Cost/year
- Cost breakdown graph

---

# **6. Directors Tab**

## **6.1 Purpose**
Central management of reusable elements:
- Usage Data Sources
- Tariff Plans
- System Profiles

## **6.2 Each Director Item Shows**
- Name
- Type (Supplier Sync, Inverter Sync, etc.)
- Linked simulations
- Actions:
    - **Link** → modal listing simulations to link
    - **Copy** → modal listing simulations to duplicate into
    - **Edit** (for tariff plans and profiles)

## **6.3 Link/Copy Modal**
- Title: “Link/Copy [Element]”
- List of simulations with checkboxes
- Buttons:
    - **Apply Link**
    - **Create Copies**

---

# **7. Wizard (New or Edit Simulation)**

## **7.1 Steps**
1. Location
2. Data Source (Supplier Sync / Inverter Sync / National Average)
3. Usage Data (upload or sync)
4. System Components (PV, battery, EV, HP, HW)
5. Tariff Plan selection
6. Results summary (cost/year)

## **7.2 Wizard Entry Options**
- Start from scratch
- Start from copy
- Start from linked elements

---

# **8. Behavioural Rules**

### **8.1 Selecting a simulation**
- Sets it as the active simulation
- Navigates to Dashboard
- Dashboard updates to reflect selected simulation

### **8.2 Editing a simulation**
- Opens wizard with preloaded values
- Saving updates the simulation and returns to Dashboard

### **8.3 Deleting a simulation**
- Removes it from the list
- If it was active, clear active simulation and show empty dashboard state

---

# **9. Non‑Functional Requirements**
- UI must remain responsive on mobile and tablet layouts
- All navigation must preserve simulation context
- Directors actions must never silently overwrite simulation data
- Comparison views must handle 2–6 simulations without layout collapse

