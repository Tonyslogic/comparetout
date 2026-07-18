# Play Console Robo script — Eco Power Optimiser walkthrough

This project ships a hand-written [Robo script](https://firebase.google.com/docs/test-lab/android/robo-scripts-reference) that drives a guided happy-path walkthrough of the app. The same JSON is consumed by:

1. **Firebase Test Lab** — the `robo` job in [`.github/workflows/ftl-android.yml`](../../.github/workflows/ftl-android.yml) runs the script every time the workflow is triggered manually.
2. **Google Play Console** — uploaded once under *Pre-launch report → Settings → Robo script*, after which every internal-testing release runs the walkthrough as part of its automatic pre-launch report.

Why hand-written and not Robo-recorded? Recorded scripts pin to whatever selectors the recorder happened to capture (often `resourceId` values that change with Compose recomposition). Hand-written gives us stable selectors — the [Phase 4A `Wizard section: $title` content descriptions](../../plans/roboscript/robo-plan.md#phase-a-selector-hardening-production-code--ships-first-as-a-standalone-pr) and [Phase 4B `Try with sample data` button text](../../plans/roboscript/robo-plan.md#phase-b-first-run-load-sample-data-affordance-production-feature--landed-2026-05-31) — that survive refactors.

---

## Script overview

File: [`playstore/robo-scripts/walkthrough.json`](../../playstore/robo-scripts/walkthrough.json)

The script is built around two principles:

1. **Expand before you screenshot.** The dashboard and Compare screens are accordion-based and default to collapsed, so a naive tab tour captures headers, not data. Every data surface is explicitly expanded before the crawl moves on.
2. **Every action must be reliably findable, because a missing selector is fatal.** Robo does **not** treat an unmatched scripted action as a no-op — it aborts the run ("Robo script failed on action N of M"), and every later step is lost. So the script only ever targets single-tap elements that are reliably present and on-screen. It deliberately avoids full-screen pickers, multi-step configuration, scroll-dependent targets, and "defensive" taps on elements that might not appear — each of those is a hard-failure risk. (An earlier revision that configured a full Compare result via the Sources/Filter pickers died on the second picker; that block is now a light-touch accordion expansion.)

| Step | Event | Selector | Notes |
|---:|---|---|---|
| 1 | `WAIT` 5 s | — | Lets the Dashboard finish first-frame render before any tap. |
| 2 | `PERMISSIONS_REQUEST` | `POST_NOTIFICATIONS` | API 33+ shows the notification runtime prompt on first launch. Robo auto-handles system permission dialogs; this event makes the expectation explicit. |
| 3 | `VIEW_CLICKED` | `text="Got it"` | **Region-mismatch dialog** — FTL devices are US-locale, the ie edition warns once on first launch (`UI2MainActivity.maybeShowRegionMismatch`). Shown *after* the disclaimer, so it stacks on top and is dismissed first. Reliably present on every fresh US-locale install of the ie edition. |
| 4 | `VIEW_CLICKED` | `text="OK"` | **First-use disclaimer** (`UI2MainActivity.maybeShowDisclaimer`) — non-cancelable, appears on every fresh install, revealed once the region dialog is gone. |
| 5 | `WAIT` 2 s | — | Dialog dismiss animations settle. |
| 6 | `VIEW_CLICKED` | `text="Try with sample data"` | Empty-state CTA (Phase 4B). Inserts one scenario + two plans, enqueues PVGIS→GenerateLoad→Simulate→Cost on the `"Simulation"` unique-work chain. |
| 7 | `WAIT` 120 s | — | Generous budget for the chain on FTL hardware. PVGIS ≈ 30 s, GenerateLoad ≈ 5 s, Simulate ≈ 30 s, Cost ≈ 10 s. |
| 8 | `VIEW_CLICKED` | `text="Scenarios"` | Bottom nav. Lands on `UI2SimulationsFragment`. |
| 9 | `VIEW_CLICKED` | `text="Sample · First Run"` | Row tap — sets active selection, returns to Dashboard. |
| 10 | `VIEW_CLICKED` + `WAIT` 3 s | `text="Tariff Plan"` | Expands the costing accordion — screenshot shows the per-plan cost table for both sample plans. |
| 11 | `VIEW_CLICKED` + `WAIT` 3 s | `text="Explore data"` | Expands the KPI pie charts. **Also required for step 12** — the graphs icon only renders while this card is expanded. |
| 12 | `VIEW_CLICKED` + `WAIT` 5 s | `contentDescription="View graphs"` | Opens the Graphs screen — bar chart + period totals with real simulation data. |
| 13 | `PRESSED_BACK` | — | Back to Dashboard. |
| 14 | `VIEW_CLICKED` + `WAIT` 3 s | `text="KPIs"` | Expands the KPI summary accordion. |
| 15 | `VIEW_CLICKED` + `WAIT` 3 s | `text="Visual overview"` | Expands the system-topology diagram. |
| 16 | `VIEW_CLICKED` + `WAIT` 2 s | `text="Comparisons"` | Bottom nav. Lands on the Compare screen with its accordions collapsed. |
| 17 | `VIEW_CLICKED` + `WAIT` 2 s | `text="Sources"` | Expands the Sources accordion — shows the subject/plan selector rows. Single tap; **no picker is opened** (the old select-all/Done picker dance is what caused the action-22 failure). |
| 18 | `VIEW_CLICKED` + `WAIT` 2 s | `text="Filter"` | Expands the Filter accordion — shows the cost/energy series chips. (Opening it collapses Sources; one accordion is open at a time.) |
| 19 | `VIEW_CLICKED` + `WAIT` 3 s | `text="Directors"` | Bottom nav. |
| 20 | `VIEW_CLICKED` | `contentDescription="Menu"` | Right-side drawer. |
| 21 | `VIEW_CLICKED` + `WAIT` 3 s | `text="Supplier Plans"` | Drawer item (capital P — distinct from Compare's `"Supplier plans"` row). Opens the plan list showing both sample plans. |
| 22 | `PRESSED_BACK` | — | Back to the main activity. |
| 23 | `VIEW_CLICKED` | `text="Scenarios"` | Bottom nav. |
| 24 | `VIEW_CLICKED` ×2 | `contentDescription="Options"` → `text="Edit"` | The sample row's ⋮ menu → Edit, then `WAIT` 2 s. Opens `UI2WizardActivity` on the **populated** sample scenario — sections are unlocked and carry real config, unlike new-scenario mode where everything is locked behind Start. |
| 25 | `VIEW_CLICKED` × 6 | `contentDescription="Wizard section: Usage Data"` → Inverters → PV System → Battery → Hot Water → EV, each + `WAIT` 1.5 s | Phase 4A's parameterized semantics. Sections stay expanded cumulatively, so each screenshot is richer than the last. |
| 26 | `PRESSED_BACK` | — | Exit the wizard. Returns to Scenarios. No unsaved-changes dialog appears (we never edited), so there is deliberately **no** trailing "Leave" tap — that would be an always-missing action and abort the run on its last step. |

Robo records continuous screenshots while crawling, so the screenshot for each step is captured automatically — no explicit `SCREENSHOT` events needed in the script. The short `WAIT`s after each expansion give animations and async loads time to settle so the ambient screenshots capture rendered data, not spinners.

---

## How the FTL job runs it

The `robo` job in [`.github/workflows/ftl-android.yml`](../../.github/workflows/ftl-android.yml) runs in parallel with the existing instrumentation `ftl` job. Same WIF auth, same results bucket. It runs the same script on a **phone and a 10" tablet**, both portrait (API 34) — the script's purpose is the Play-Store screenshot path, and Play Store requires tablet screenshots as listing assets, so both form factors are captured in one run. The tablet's larger canvas also produces the richer captures (more of each accordion/table visible per frame). Both models are quota-free; the 4-device instrumentation job continues to provide cross-device *code* validation.

```bash
gcloud firebase test android run \
  --type robo \
  --app app/build/outputs/apk/ie/debug/app-ie-debug.apk \
  --robo-script playstore/robo-scripts/walkthrough.json \
  --device "model=MediumPhone.arm,version=34,locale=en,orientation=portrait" \
  --device "model=MediumTablet.arm,version=34,locale=en,orientation=portrait" \
  --results-bucket=comparetout-ftl-results \
  --timeout 15m
```

### Where the FTL output lives

After a run completes:

- **Firebase console** — [console.firebase.google.com/project/comparetout-ftl/testlab](https://console.firebase.google.com/project/comparetout-ftl/testlab). Pick the matrix, expand the device, see the video + ordered screenshots.
- **Direct GCS access** — `gs://comparetout-ftl-results/<run-id>/` contains the per-step screenshots under `artifacts/`.

The 30-day lifecycle rule on the results bucket also applies to Robo artifacts.

---

## How to upload the script to Play Console

The same `walkthrough.json` works in Play Console as in FTL. Upload it once and every pre-launch report on every internal-testing build runs it automatically.

1. Go to [Play Console](https://play.google.com/console/) → your app.
2. Left sidebar: **Test and release → Pre-launch report → Settings**.
3. Scroll to the **Robo script** section.
4. Click **Upload Robo script** and pick `playstore/robo-scripts/walkthrough.json` from this repo.
5. Save.

That's the entire setup. From the next internal-testing build onward, the pre-launch report will execute this script before falling through to its standard random Robo crawl.

### Where Play Console exposes the screenshots

After uploading a new internal-testing release:

1. **Test and release → Pre-launch report** (left sidebar) → click the latest report.
2. **Screenshots** tab — one column per device the pre-launch report ran on, one row per step the Robo crawl captured.
3. The scripted steps come first (in order); any post-script random-crawl screenshots follow.
4. Use the device dropdown to switch between Phone / 7" tablet / 10" tablet variants.

Pre-launch reports typically take ~30 minutes from upload to availability. They're billed against your Play Console's free quota and don't consume FTL quota.

---

## When to update the script

The script depends on specific UI selectors. Update it whenever any of these change:

| Selector source | What changes break the script | Where to check |
|---|---|---|
| `text="Got it"` / `text="OK"` | Renaming the region-mismatch ack (`ui2_got_it`) or disclaimer (`dialog_ok`) buttons, or changing when the startup dialogs fire / their stacking order | [`UI2MainActivity.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2MainActivity.kt) `maybeShowRegionMismatch` / `maybeShowDisclaimer` |
| `text="Try with sample data"` | Renaming the empty-state button or the drawer menu item | [`UI2NavigationDrawer.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2NavigationDrawer.kt), [`UI2DashboardFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2DashboardFragment.kt) `EmptyDashboardSampleCard` |
| `text="Sample · First Run"` | Renaming the bundled sample scenario | [`SampleDataLoader.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/SampleDataLoader.kt) `SAMPLE_PREFIX`, [`app/src/main/assets/samples/sample_scenario.json`](../../app/src/main/assets/samples/sample_scenario.json) |
| `text="Scenarios" / "Comparisons" / "Directors"` | Renaming bottom-nav items | [`app/src/main/res/menu/bottom_nav_menu.xml`](../../app/src/main/res/menu/bottom_nav_menu.xml) |
| `text="Tariff Plan" / "Explore data" / "KPIs" / "Visual overview"` | Renaming the dashboard accordion titles (`ui2_dash_tariff_plan`, `ui2_dash_explore`, `ui2_dash_kpis`, `ui2_dash_visual_overview`) | [`UI2DashboardFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2DashboardFragment.kt) simulation-mode `ExpandableCard`s / `KpiAccordion` |
| `contentDescription="View graphs"` | Removing the cd on the Explore-data graph icon — note the icon only exists while the card is **expanded** and KPIs are loaded, which is why the script expands `Explore data` first | [`UI2DashboardFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2DashboardFragment.kt) `ui2_dash_view_graphs` usages |
| `text="Sources" / "Filter" / "Simulations" / "Supplier plans"` | Renaming the Compare accordions or picker rows (`ui2_cmp_sources_title`, `ui2_cmp_filter_title`, `ui2_cmp_simulations`, `ui2_supplier_plans`) | [`CompareScreen.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/CompareScreen.kt) `AccordionCard` / `SourcesSection` |
| `text="Select all" / "Done"` | Renaming the picker-sheet buttons (`ui2_cmp_select_all`, `ui2_done`) | [`CompareScreen.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/CompareScreen.kt) `SelectSheet` |
| `text="Net" / "Buy" / "Sell"` | Renaming the basic cost-series chips (`ui2_cmp_ser_net/buy/sell`) | [`UI2CompareViewModel.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2CompareViewModel.kt) `COST_SERIES` / `BASIC_COST_IDS` |
| `text="Supplier Plans"` (drawer, capital P) | Renaming the drawer item (`ui2_drawer_supplier_plans`) | [`UI2NavigationDrawer.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2NavigationDrawer.kt) |
| `contentDescription="Menu"` | Renaming/removing the drawer button cd | [`UI2WizardActivity.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2WizardActivity.kt) and other UI2 activities' menu IconButtons |
| `contentDescription="Options"` / `text="Edit"` | Renaming the scenario row's ⋮ menu cd (`ui2_options`) or the Edit item (`ui2_edit`) | [`UI2SimulationsFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2SimulationsFragment.kt) `SimulationCard` |
| `contentDescription="Wizard section: $title"` | Changing the title text of a wizard accordion, or renaming the cd format | [`UI2WizardActivity.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2WizardActivity.kt) `WizardAccordionSection` |

If a step's selector goes stale, the FTL `--robo-script` run **aborts** with `Robo script failed on action N of M` and every later step is lost (this is not a soft "skip and crawl" — a single unfindable action is fatal to the scripted portion). Play Console pre-launch is more forgiving and falls back to random crawl, but the scripted screenshots past the failure are still gone. Either way, watch for a run that ends early or a `failed on action N` message as the signal that action N's selector has drifted — cross-reference N against the step table above.

---

## Limits

- **Post-script random crawl is not disabled.** After the scripted steps finish, FTL Robo and Play Console pre-launch reports continue with their standard random crawl. That's actually useful — random crawl explores edges the script doesn't — but it does mean the artifact output isn't *only* the scripted walkthrough.
- **No assertions.** Robo scripts can't assert "the scenario list now has 1 entry". They can only drive UI actions. Correctness validation lives in the instrumentation `Smoke*Test` classes (see [`docs/firebase/README.md`](../firebase/README.md#test-coverage)).
- **One long linear script is inherently brittle.** Because an unmatched action aborts the run (above), the more steps a single script has — and the more of them depend on fragile state (open sheets, scrolling, adaptive layouts) — the more likely a run dies partway. This script is kept lean and to reliably-present single-tap targets for that reason. If you need deep coverage of a complex flow (e.g. a fully-configured Compare result), prefer a separate short focused script over bolting more steps onto this one; Play Console still only accepts one script, so that extra coverage belongs in an ad-hoc FTL run, not the walkthrough.
- **Startup dialogs.** On a fresh install the first-use disclaimer always appears, and on a device whose country doesn't match the edition (FTL devices are US-locale; the ie edition warns) the region-mismatch dialog stacks on top of it. Steps 3–4 dismiss them in stacking order ("Got it" then "OK"). If a new first-launch dialog is ever added, the script must gain a dismiss step for it — the symptom is every subsequent click landing on the dialog scrim.
- **PVGIS dependency.** Step 7 (the 120 s wait) assumes the live PVGIS HTTPS call succeeds. If `re.jrc.ec.europa.eu` is down or rate-limiting, the simulation chain won't complete, and downstream steps that depend on populated dashboard data will produce uninteresting screenshots (the dashboard's "needs attention" accordion instead of charts). Re-running typically resolves it.
- **First-run only.** The empty-state button only appears when `dataSourceInfo == null && !hasScenarios`. If a Play Console pre-launch slot somehow carries DB state across runs (it shouldn't — each pre-launch report installs the APK fresh), the script's step 6 will skip and downstream steps that depend on the sample scenario existing will need the user to have already loaded it. Watch first runs after a Play Console environment change.

---

## Related

- [`plans/roboscript/robo-plan.md`](../../plans/roboscript/robo-plan.md) — full Phase 4 (A/B/C/D) plan and implementation history.
- [`docs/firebase/README.md`](../firebase/README.md) — Firebase Test Lab setup, IAM bindings, troubleshooting.
- [`playstore/robo-scripts/walkthrough.json`](../../playstore/robo-scripts/walkthrough.json) — the script itself.
