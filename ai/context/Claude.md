# CLAUDE — PROJECT SYSTEM RULES (SHORT VERSION)

Repo: Eco Power Optimiser (internal codename "comparetout" / "TOUTC").
Current focus: **the simulation engine refactor.** Plan and phases: `plans/sim/refactor.md`.

------------------------------------------------------------
## 1. SIMULATION ENGINE REFACTOR (functional changes EXPECTED)
The simulation engine (`SimulationWorker` and the new `scenario/sim` package) is being deliberately
refactored and extended — arbitrary periods, millis-since-epoch time, pluggable components (e.g. heat
pump), a hybrid DC/AC bus model, and new selectable inverter dispatch modes. Here, changing
domain/simulation logic IS the job.
- Bug fixes and behavioural changes to the simulation are in scope and expected.
- Model, JSON, importer/exporter, DAO, and UI edits are permitted — but REVIEW with the user first.
- DB schema changes require EXPLICIT approval. The `millisSinceEpoch` columns already exist in
  `loadprofiledata` / `paneldata` / `scenariosimulationdata`, so no migration is needed to start engine work.
- Testing and initial refactoring MUST require zero schema change.
- Preserve behaviour where a refactor is meant to be behaviour-preserving, and prove it with the
  golden-master tests. Surface intended behaviour changes as explicit, reviewed deltas.

------------------------------------------------------------
## 2. ALWAYS
- Costing is recompute-missing-only: saving a scenario/plan MUST delete its stale sim + costing rows,
  or figures go stale forever.
- Don't gratuitously rename `ScenarioDAO` / `ToutcRepository` / model classes.
- Default to unified diffs for edits; ask for missing context instead of guessing.
- Store any file summaries under `ai/context/summaries`.

------------------------------------------------------------
## 3. PRIMARY OBJECTIVE
Produce correct, well-tested simulation engine changes that respect the schema-approval rule and the
costing-invalidation rule, with bugs fixed and clearly explained.
