# Play Console Robo script — Eco Power Optimiser walkthrough

This project ships a hand-written [Robo script](https://firebase.google.com/docs/test-lab/android/robo-scripts-reference) that drives a guided happy-path walkthrough of the app. The same JSON is consumed by:

1. **Firebase Test Lab** — the `robo` job in [`.github/workflows/ftl-android.yml`](../../.github/workflows/ftl-android.yml) runs the script every time the workflow is triggered manually.
2. **Google Play Console** — uploaded once under *Pre-launch report → Settings → Robo script*, after which every internal-testing release runs the walkthrough as part of its automatic pre-launch report.

Why hand-written and not Robo-recorded? Recorded scripts pin to whatever selectors the recorder happened to capture (often `resourceId` values that change with Compose recomposition). Hand-written gives us stable selectors — the [Phase 4A `Wizard section: $title` content descriptions](../../plans/roboscript/robo-plan.md#phase-a-selector-hardening-production-code--ships-first-as-a-standalone-pr) and [Phase 4B `Try with sample data` button text](../../plans/roboscript/robo-plan.md#phase-b-first-run-load-sample-data-affordance-production-feature--landed-2026-05-31) — that survive refactors.

---

## Script overview

File: [`playstore/robo-scripts/walkthrough.json`](../../playstore/robo-scripts/walkthrough.json)

The script is built around four principles:

1. **Expand before you screenshot.** The dashboard and Compare screens are accordion-based and default to collapsed, so a naive tab tour captures headers, not data. Every data surface is explicitly expanded before the crawl moves on.
2. **Every action must be reliably findable, because a missing selector is fatal.** Robo does **not** treat an unmatched scripted action as a no-op — it aborts the run ("Robo script failed on action N of M"), and every later step is lost. So the script only ever targets single-tap elements that are reliably present and on-screen. It deliberately avoids full-screen pickers, multi-step configuration, and "defensive" taps on elements that might not appear — each of those is a hard-failure risk.
3. **Navigate at scroll-top; make the deep-scroll screen terminal.** *Both* chrome bars hide on scroll — the bottom nav via Material's `HideBottomViewOnScrollBehavior` ([`activity_ui2_main.xml`](../../app/src/main/res/layout/activity_ui2_main.xml)) and the top app bar via Compose `enterAlwaysScrollBehavior` ([`UI2DashboardFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2DashboardFragment.kt) `DashboardScreen`). So *any bottom-nav tab tap that follows an in-screen scroll aborts the run* — the bar it needs has slid off-screen. Every earlier long-linear revision died exactly there (action 21 portrait, 27 then 19 landscape — always a tab tap after a scroll; orientation was a red herring). The structural fix, not a per-tap patch: **do every bottom-nav tab tap first, while the app is at scroll-top and both bars are guaranteed visible; save the one screen that requires deep scrolling (the Dashboard accordion tour, ending on `Visual overview`) for last, after the final tab tap the script will ever make.** Expanding a *top-of-screen* accordion is a tap, not a scroll, so it doesn't hide the bars — that's why `Sources` on Compare is safe to open mid-tour but `Filter` (lower down) is not, and is left to the random crawl.
4. **Prefer self-correcting primitives over blind waits.** Startup dialogs are **conditional actions** (`contextDescriptor: element_present`) — they fire whenever the dialog is on-screen and are *skipped, never fatal,* when it isn't, so no assumption about locale/stacking order can abort the run. Every navigation tap is preceded by `WAIT_FOR_ELEMENT` on its target rather than a blind `WAIT`, so a slow render or async tab-visibility read can't make the tap miss. The single unavoidable scroll (to reach `Visual overview`) is a `VIEW_SWIPED` `Forward` and sits **dead last**, so even a residual miss there costs only that one screenshot — everything else is already captured. (`ELEMENT_SCROLL_INTO_VIEW` is *not* usable here: it keys off a scroll-container `resourceId`, and the Dashboard is a Compose `Column.verticalScroll` with no resource ids.)

The flow, by phase (see [`walkthrough.json`](../../playstore/robo-scripts/walkthrough.json) for the authoritative action list):

| Phase | Actions | What happens |
|---|---|---|
| **Startup dialogs** (conditional) | `VIEW_CLICKED text="Got it"`, `VIEW_CLICKED text="OK"` | Region-mismatch ack (`maybeShowRegionMismatch`, US-locale FTL devices on the ie edition) and first-use disclaimer (`maybeShowDisclaimer`). Both are `element_present` conditionals with `maxNumberOfRuns:3` — they dismiss whenever/whichever appears and never abort if absent. Two plain `WAIT`s after `PERMISSIONS_REQUEST` give Robo the between-action slots it uses to evaluate these. |
| **Load sample + simulate** | `PERMISSIONS_REQUEST` → `WAIT_FOR_ELEMENT`/`VIEW_CLICKED text="Try with sample data"` → `WAIT` 120 s | Empty-state CTA (Phase 4B) enqueues PVGIS→GenerateLoad→Simulate→Cost on the `"Simulation"` chain. The 120 s wait is the one genuinely time-based step (no UI element reliably signals "sim done"). |
| **Pin the scenario** | `text="Scenarios"` (tab) → `text="Sample · First Run"` (row) | Sets the active subject and returns to the Dashboard, now populated. |
| **Tab tour — at scroll-top, bars visible** | `text="Comparisons"` → open `text="Sources"` (top accordion, no scroll) · `text="Directors"` → `contentDescription="Menu"` → `text="Supplier Plans"` → `PRESSED_BACK` · `text="Scenarios"` → `contentDescription="Options"` → `text="Edit"` → six `contentDescription="Wizard section: …"` (Usage Data→Inverters→PV System→Battery→Hot Water→EV) → `PRESSED_BACK` | Every bottom-nav tab is visited while nothing has scrolled, so each tap has an on-screen target. `Sources` is the only accordion opened here (top of Compare, a tap not a scroll). The wizard opens on the **populated** sample scenario (`Options → Edit`), so its sections are unlocked; sections stay cumulatively expanded. |
| **Terminal deep-dive — Dashboard, scroll-heavy, no tab tap after** | `text="Dashboard"` (tab, from the Scenarios list at scroll-top) → `text="Tariff Plan"` → `text="Explore data"` → `contentDescription="View graphs"` → `PRESSED_BACK` → `text="KPIs"` → `VIEW_SWIPED Forward` → `text="Visual overview"` | The four Dashboard accordions in on-screen order. `View graphs` only renders while `Explore data` is expanded. `Visual overview` sits below the fold, so a single `Forward` swipe brings it up before the final tap. Because no bottom-nav tab is tapped after this, the hidden-bar failure mode is designed out. |

Robo records continuous screenshots while crawling, so each step's screenshot is captured automatically — no explicit `SCREENSHOT` events needed. The short `WAIT`s after each expansion let animations and async loads settle so the ambient screenshots capture rendered data, not spinners.

> **Action numbers:** the table above is a *phase-level* narrative. The JSON counts each `WAIT`, `WAIT_FOR_ELEMENT`, `VIEW_SWIPED`, and conditional as its own action (62 total), so a "Robo script failed on action N of M" message must be cross-referenced against [`walkthrough.json`](../../playstore/robo-scripts/walkthrough.json) itself (the authoritative action list), not this table.

---

## How the FTL job runs it

The `robo` job in [`.github/workflows/ftl-android.yml`](../../.github/workflows/ftl-android.yml) runs in parallel with the existing instrumentation `ftl` job. Same WIF auth, same results bucket. It runs the same script on a **phone (portrait) and a 10" tablet (landscape)** at API 34 — the two orientations Play Store lists phone vs tablet screenshots in. The tablet's larger canvas also produces the richer captures (more of each accordion/table visible per frame). Both models are quota-free; the 4-device instrumentation job continues to provide cross-device *code* validation.

```bash
gcloud firebase test android run \
  --type robo \
  --app app/build/outputs/apk/ie/debug/app-ie-debug.apk \
  --robo-script playstore/robo-scripts/walkthrough.json \
  --device "model=MediumPhone.arm,version=34,locale=en,orientation=portrait" \
  --device "model=MediumTablet.arm,version=34,locale=en,orientation=landscape" \
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
- **A long linear script is brittle — mitigated, not eliminated.** Because an unmatched action aborts the run (above), each added step depending on fragile state (open sheets, scrolling, adaptive layouts) is another failure point. This revision fights that with the scroll-top-navigation ordering (principle 3), `WAIT_FOR_ELEMENT` guards, and conditional dialog handlers (principle 4), so no single flaky render or hidden bar takes the run down — but the backbone taps are still sequential and a genuinely-missing selector mid-backbone still aborts everything after it. Keep the script lean and to reliably-present single-tap targets. If you need deep coverage of a complex flow (e.g. a fully-configured Compare result), prefer a separate short focused script over bolting more steps onto this one; Play Console only accepts one script, so that extra coverage belongs in an ad-hoc FTL run, not the walkthrough.
- **Startup dialogs are handled conditionally.** On a fresh install the first-use disclaimer always appears, and on a US-locale device (all FTL devices) the ie edition's region-mismatch dialog stacks on top of it. The script's first two entries are **conditional actions** (`element_present` on `"Got it"` / `"OK"`, `maxNumberOfRuns:3`) that fire whenever either dialog is on-screen, in whatever order it appears, and are skipped without error if it doesn't — so no change to dialog stacking, locale, or timing can abort the run. If a new first-launch dialog is added, give it its own conditional handler; the symptom of a missed one is every subsequent click landing on the dialog scrim.
- **PVGIS dependency.** Step 7 (the 120 s wait) assumes the live PVGIS HTTPS call succeeds. If `re.jrc.ec.europa.eu` is down or rate-limiting, the simulation chain won't complete, and downstream steps that depend on populated dashboard data will produce uninteresting screenshots (the dashboard's "needs attention" accordion instead of charts). Re-running typically resolves it.
- **First-run only.** The empty-state button only appears when `dataSourceInfo == null && !hasScenarios`. If a Play Console pre-launch slot somehow carries DB state across runs (it shouldn't — each pre-launch report installs the APK fresh), the script's step 6 will skip and downstream steps that depend on the sample scenario existing will need the user to have already loaded it. Watch first runs after a Play Console environment change.

---

## Related

- [`plans/roboscript/robo-plan.md`](../../plans/roboscript/robo-plan.md) — full Phase 4 (A/B/C/D) plan and implementation history.
- [`docs/firebase/README.md`](../firebase/README.md) — Firebase Test Lab setup, IAM bindings, troubleshooting.
- [`playstore/robo-scripts/walkthrough.json`](../../playstore/robo-scripts/walkthrough.json) — the script itself.
