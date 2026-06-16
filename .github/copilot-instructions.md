# GLOBAL DEVELOPMENT SPEC — Eco Power Optimiser

## 0. PROJECT OVERVIEW
This repo is the Android app **Eco Power Optimiser** (internal codename "comparetout" / "TOUTC"):
it simulates home energy systems (PV, batteries, inverters, hot water, EV charging) against tariff
plans to estimate cost/benefit.

Current focus: a **refactor and extension of the simulation engine** — `SimulationWorker` and the new
`scenario/sim` package. Goals: simulate arbitrary periods, use millis-since-epoch time consistently,
support pluggable components (e.g. a heat pump), model hybrid inverters with separate DC/AC buses, and
add new selectable inverter dispatch modes. Functional changes to simulation/domain code are EXPECTED.
Plan and phases: `plans/sim/refactor.md`.

All generated code MUST comply with the rules below.

------------------------------------------------------------
## 1. ARCHITECTURE RULES (STRICT — DO NOT VIOLATE)

1.1 SCOPE OF CHANGE
- Simulation/domain changes are in scope; this is the job. Bug fixes and behavioural changes to the
  simulation are expected and welcome.
- Model, JSON, importer/exporter, DAO, repository, and UI edits are permitted — but REVIEW with the
  user before making them. Do NOT make sweeping edits to these layers unprompted.

1.2 DATABASE SCHEMA
- DB schema changes require EXPLICIT human approval, every time.
- The `millisSinceEpoch` columns ALREADY EXIST (nullable) in `loadprofiledata`, `paneldata`, and
  `scenariosimulationdata` — so no migration is needed to begin engine work. Where legacy rows hold
  NULL millis, derive it from `date` + `mod` at the I/O boundary.
- Testing and the initial refactoring MUST require zero schema change.

1.3 BEHAVIOUR PRESERVATION
- A refactor that is meant to preserve behaviour MUST be proven so by the golden-master / characterization
  tests. Intended behaviour changes (bug fixes, the hybrid bus model, new dispatch modes) must be surfaced
  as explicit, reviewed deltas — never smuggled in.
- The default inverter dispatch order (load → battery → grid) is UNCHANGED. The new load → grid → battery
  order is an ADDED, per-inverter selectable mode, not a change to existing scenarios.

1.4 COSTING INVALIDATION
- Costing is recompute-missing-only. Saving a scenario/plan MUST delete its stale simulation + costing
  rows, or figures go stale forever. Preserve this invariant in any change that writes scenario data.

1.5 NAMING / STRUCTURE
- Do NOT gratuitously rename `ScenarioDAO`, `ToutcRepository`, or model classes.
- Do NOT move files across modules or rename packages unless explicitly instructed.

------------------------------------------------------------
## 2. ENGINE DESIGN CONVENTIONS
- Keep the simulation engine PURE: no `Context`, `Worker`, Room, or LiveData dependency. `SimulationWorker`
  is a thin adapter (read via repository → run engine → persist → notify).
- The engine is unit-tested with plain JUnit4 (no Mockito/Robolectric — match the existing test style).
- Favour the design patterns already chosen in the plan: Iterator (`TimeAxis`), Strategy (`DispatchStrategy`),
  role interfaces + pipeline for the per-interval solve, Factory/registry for components, Builder for tests.
- New simulation components implement the role interfaces and register with the component registry; see
  `docs/adding-a-simulation-component.md` (heat pump worked example) once it exists.

------------------------------------------------------------
## 3. SAFETY RULES FOR CODE GENERATION

3.1 DO NOT:
- Change the DB schema without explicit approval.
- Change the default dispatch order or silently alter existing-scenario results.
- Break costing invalidation.
- Generate code that cannot compile.

3.2 ALWAYS:
- Validate assumptions before generating code; ask for missing context rather than guessing.
- Keep behaviour-preserving refactors provably so (golden-master tests).
- Produce minimal, reviewable diffs when modifying existing files.

------------------------------------------------------------
## 4. OUTPUT FORMAT RULES
- Provide complete, compilable Java snippets.
- When modifying existing code, provide a unified diff OR a clearly marked before/after section.
- Do NOT rewrite entire files unless explicitly asked.

------------------------------------------------------------
## 5. MODEL-SPECIFIC GUIDANCE

### For Claude:
- You may use long-context reasoning. Strictly follow the architecture and schema-approval rules above.
- Do not hallucinate new logic; request missing files or context when needed.

### For Gemini / GPT / other assistants:
- Follow all global rules above. Defer architecture-heavy reasoning and engine design decisions to the
  plan in `plans/sim/refactor.md`.
- Provide glue code, reasoning, and debugging support without modifying the schema or breaking the
  costing/dispatch invariants.

------------------------------------------------------------
## 6. PRIMARY OBJECTIVE
Produce correct, well-tested simulation engine changes that respect the schema-approval and
costing-invalidation rules, with bugs fixed and clearly explained.
