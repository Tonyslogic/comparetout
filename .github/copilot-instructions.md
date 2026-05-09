# GLOBAL DEVELOPMENT SPEC — ANDROID UI REPLACEMENT PROJECT

## 0. PROJECT OVERVIEW
This project is a full UI replacement for an existing Android application.
The backend logic, data layer, domain layer, and ViewModel contracts are all stable and MUST NOT be modified.
The redesign collapses ~20 legacy screens into 4–5 new, more complex screens.
Only UI code is being replaced. No functional changes are allowed.

All generated code MUST comply with the rules below.

------------------------------------------------------------
## 1. ARCHITECTURE RULES (STRICT — DO NOT VIOLATE)
These rules override all others.

1.1 BACKEND IMMUTABILITY
- Do NOT modify repositories, data sources, DTOs, API calls, or domain logic.
- Do NOT introduce new backend calls.
- Do NOT change method signatures in repositories or use cases.
- Do NOT change business logic.

1.2 VIEWMODEL CONTRACTS
- ViewModels MUST NOT be renamed, removed, or structurally altered.
- Public functions, state flows, and event handlers MUST remain intact.
- You may add UI‑only helper functions, but they must not affect business logic.

1.3 NAVIGATION CONTRACTS
- Navigation routes, arguments, and deep links MUST remain compatible.
- You may consolidate screens, but navigation destinations must remain stable.
- Do NOT introduce new navigation parameters unless explicitly instructed.

1.4 DI / DEPENDENCY GRAPH
- Do NOT modify Hilt modules, qualifiers, or bindings.
- Do NOT introduce new injected dependencies.

1.5 FILE STRUCTURE
- Do NOT move files across modules unless explicitly instructed.
- Do NOT rename modules or packages.

1.6 EXISTING CODE
- STOP and ASK if there is a need to modify existing code
- Application level utilities SHALL be reused if needed

------------------------------------------------------------
## 2. UI REDESIGN RULES (PRIMARY FOCUS)
These rules define how the new UI must be built.

2.1 TECHNOLOGY
- Use Jetpack Compose exclusively for new UI.
- Use Material 3 components.
- Follow unidirectional data flow (UDF).
- State must be hoisted to ViewModels.

2.2 COMPOSABLE DESIGN RULES
- Composables must be pure and side‑effect free.
- Use @Composable functions with clear parameters.
- Avoid passing ViewModels directly into composables.
- Use state holders (StateFlow, collectAsState) at screen level only.

2.3 COMPONENT LIBRARY
Use the following preferred components:
- Scaffold, TopAppBar, NavigationBar, NavigationRail
- LazyColumn / LazyRow
- Card, Surface, ElevatedCard
- TextField, OutlinedTextField
- Button, FilledTonalButton, IconButton
- ModalBottomSheet, AlertDialog

2.4 THEME & STYLING
- Use MaterialTheme.colorScheme and MaterialTheme.typography.
- Do NOT hardcode colors, padding, or typography.
- Use dimension resources or theme spacing tokens.

2.5 ACCESSIBILITY
- All interactive elements must have contentDescription.
- Touch targets must meet minimum size requirements.
- Text must respect dynamic font scaling.

2.6 SCREEN CONSOLIDATION RULES
- The new UI reduces ~20 screens into 4–5.
- Consolidation must preserve all original functionality.
- Consolidated screens must expose the same ViewModel events and state.

------------------------------------------------------------
## 3. NAMING & STRUCTURE RULES

3.1 FILE NAMING
- Screens: <Feature>Screen.kt
- Components: <Feature><Component>.kt
- State classes: <Feature>UiState.kt
- Events: <Feature>UiEvent.kt

3.2 PACKAGE STRUCTURE
- feature/<name>/ui/
- feature/<name>/viewmodel/
- feature/<name>/navigation/
- core/ui/components/
- core/ui/theme/

3.3 COMPOSABLE NAMING
- Top‑level screen: <Feature>Screen
- Subcomponents: <Feature><Component>
- Avoid abbreviations.

3.4 STATE & EVENT NAMING
- UiState: immutable data class
- UiEvent: sealed interface or sealed class
- Event handlers: on<EventName>()

------------------------------------------------------------
## 4. SAFETY RULES FOR CODE GENERATION

4.1 DO NOT:
- Invent new backend logic.
- Change ViewModel behaviour.
- Modify repository or domain code.
- Add new dependencies.
- Break navigation compatibility.
- Generate code that cannot compile.

4.2 ALWAYS:
- Validate assumptions before generating code.
- Ask for missing context if needed.
- Produce minimal diffs when modifying existing files.
- Follow the architecture and UI rules above.

------------------------------------------------------------
## 5. OUTPUT FORMAT RULES

5.1 When generating code:
- Provide complete, compilable Kotlin snippets.
- Do NOT include placeholder variables unless necessary.
- Use idiomatic Compose and Kotlin.

5.2 When modifying existing code:
- Provide a unified diff OR a clearly marked before/after section.
- Do NOT rewrite entire files unless explicitly asked.

------------------------------------------------------------
## 6. MODEL‑SPECIFIC GUIDANCE

### For Claude:
- You may use long‑context reasoning.
- You must strictly follow architecture rules.
- You must avoid hallucinating new logic.
- You may request missing files or context.

### For Gemini:
- Prioritize Android‑specific UI patterns.
- Focus on Compose, Material 3, and layout correctness.
- Defer architecture‑heavy reasoning to Claude.

### For GPT:
- Provide glue code, reasoning, and debugging support.
- Follow the global rules above.
- Avoid modifying backend logic.

------------------------------------------------------------
## 7. PRIMARY OBJECTIVE

Generate a modern, clean, Material 3 Jetpack Compose UI that:
- Preserves all existing functionality,
- Respects all backend and ViewModel contracts,
- Consolidates screens safely,
- Improves UX,
- And adheres to the rules in this spec.

This spec overrides all other instructions unless explicitly superseded.
