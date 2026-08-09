# Architecture & data model documentation

Reference material for the Eco Power Optimiser Android app (internal codename
`comparetout` / TOUTC). Written to be read by both people and language models:
prose states *why*, tables state *what*, and every claim is anchored to a file
path you can open.

**Accurate as of 2026-08-09, database version 17, 37 tables.**

## The documents

| Document | Answers |
|---|---|
| [architecture.md](architecture.md) | How the layers fit together, what runs in the background, how editions gate features. Start here. |
| [database-schema.md](database-schema.md) | What tables exist, how they relate, how the schema evolves. |
| [db-dependencies.md](db-dependencies.md) | Forward map: given a screen, worker or package, which tables does it touch? |
| [db-dependencies-by-table.md](db-dependencies-by-table.md) | Reverse map: given a table, what breaks if I change it? |

Related documents elsewhere in `docs/`:

- [../adding-a-simulation-component.md](../adding-a-simulation-component.md) —
  authoring guide for new simulation components (EV, hot water, heat pump…)
- [../dynamic-tariffs.md](../dynamic-tariffs.md) — wholesale-tracking tariffs

## Diagram formats, and which to trust

Two formats coexist deliberately:

- **Mermaid**, inline in the markdown. Renders on GitHub and in most IDEs
  without a build step, diffs cleanly, and is readable as source text. These are
  the *maintained* diagrams — when code and diagram disagree, fix these.
- **draw.io** (`*.drawio` + exported `*.svg`). Hand-laid detailed views whose
  layout carries meaning that auto-layout would destroy. Edit the `.drawio` in
  draw.io desktop and push; `.github/workflows/drawio-export.yml` re-exports the
  `.svg` automatically, so the two never drift.

### Provenance of the inherited diagrams

The `.drawio` views came from branch `copilot/fix-27`, generated against the code
as of **2025-06-22** and never reviewed. They are kept because their layout is
worth keeping, but they predate several structural changes. Treat them as
sketches until verified:

| Diagram | Confidence today | Why |
|---|---|---|
| `database-schema` | **Stale** | Drawn at 32 tables; there are now 37 (heat pump, readiness, plan combinations, transform meta) and the version is 17. |
| `simulation-worker` | Likely fair | Core loop is stable, but the component seam and per-inverter dispatch arrived after. |
| `costing-worker` | **Stale** | Predates the buy/sell decomposition and export plans. |
| `abstract-generation-worker` | Likely fair | Shape unchanged; three more sources now use it. |
| `alphaess-import-worker` | Likely fair | |
| `battery-package`, `ev-package`, `panel-package`, `load-profile-package` | Likely fair | Component shapes are stable; the DAO beneath them split. |
| `main-comparison`, `price-plan-management`, `scenario-fragments`, `import-fragments`, `import-overview-fragment`, `base-graphs-fragment` | **UI1 only** | Accurate for the Views/Fragments surface; the Compose UI2 surface is not drawn at all. |

Nothing above is a diagram of UI2, the ops layer, the profile/flavor gating or
the dynamic-tariff subsystem. Those live as Mermaid in
[architecture.md](architecture.md).

## Keeping this current

The table-level maps are derived from source, not maintained by hand. Regenerate
them after any DAO or repository change:

```bash
python scripts/depmap.py          # writes scripts/depmap.json
```

The script parses `@Query` SQL and `@Insert`/`@Update`/`@Delete` entity types to
build DAO → table edges, then follows `ToutcRepository` delegation to reach
callers. It is deliberately conservative: it reports what it can prove from the
source text and lists what it could not resolve under `unresolved_calls`, rather
than guessing. Treat a growing `unresolved_calls` list as a signal that a new
indirection layer needs teaching to the script.

What the script **cannot** see, and must therefore be maintained by hand:

- Reads and writes that pass through `model/ops/*` composite operations rather
  than a single DAO call — the fan-out is real but not statically obvious.
- Anything reached by reflection, Room's generated code, or raw SQL in
  migrations.
- The *meaning* of a dependency. That `CostingWorker` touches `costings` is
  mechanical; that it must delete stale rows or figures go permanently stale is
  the part worth writing down.
