# Play Console Robo script — Eco Power Optimiser walkthrough

This project ships a hand-written [Robo script](https://firebase.google.com/docs/test-lab/android/robo-scripts-reference) that drives a guided happy-path walkthrough of the app. The same JSON is consumed by:

1. **Firebase Test Lab** — the `robo` job in [`.github/workflows/ftl-android.yml`](../../.github/workflows/ftl-android.yml) runs the script every time the workflow is triggered manually.
2. **Google Play Console** — uploaded once under *Pre-launch report → Settings → Robo script*, after which every internal-testing release runs the walkthrough as part of its automatic pre-launch report.

Why hand-written and not Robo-recorded? Recorded scripts pin to whatever selectors the recorder happened to capture (often `resourceId` values that change with Compose recomposition). Hand-written gives us stable selectors — the [Phase 4A `Wizard section: $title` content descriptions](../../plans/roboscript/robo-plan.md#phase-a-selector-hardening-production-code--ships-first-as-a-standalone-pr) and [Phase 4B `Try with sample data` button text](../../plans/roboscript/robo-plan.md#phase-b-first-run-load-sample-data-affordance-production-feature--landed-2026-05-31) — that survive refactors.

---

## Script overview

File: [`playstore/robo-scripts/walkthrough.json`](../../playstore/robo-scripts/walkthrough.json)

| Step | Event | Selector | Notes |
|---:|---|---|---|
| 1 | `WAIT` 5 s | — | Lets the Dashboard finish first-frame render before any tap. |
| 2 | `VIEW_CLICKED` | `text="Try with sample data"` | Empty-state CTA (Phase 4B). Inserts one scenario + two plans, enqueues PVGIS→GenerateLoad→Simulate→Cost on the `"Simulation"` unique-work chain. |
| 3 | `WAIT` 120 s | — | Generous budget for the chain on FTL hardware. PVGIS ≈ 30 s, GenerateLoad ≈ 5 s, Simulate ≈ 30 s, Cost ≈ 10 s. |
| 4 | `VIEW_CLICKED` | `text="Scenarios"` | Bottom nav. Lands on `UI2SimulationsFragment`. |
| 5 | `VIEW_CLICKED` | `text="Sample · First Run"` | Row tap — sets active selection, returns to Dashboard. |
| 6 | `VIEW_CLICKED` | `contentDescription="View graphs"` | Inside the Explore Data accordion (Dashboard simulation mode). |
| 7 | `PRESSED_BACK` | — | Back to Dashboard. |
| 8 | `VIEW_CLICKED` | `text="Comparisons"` | Bottom nav. |
| 9 | `VIEW_CLICKED` | `text="Directors"` | Bottom nav. |
| 10 | `VIEW_CLICKED` | `contentDescription="Menu"` | Right-side drawer. |
| 11 | `PRESSED_BACK` | — | Dismiss drawer. |
| 12 | `VIEW_CLICKED` | `text="Scenarios"` | Re-select Scenarios so the next tap reaches the SectionHeader action. |
| 13 | `VIEW_CLICKED` | `text="+ Create new"` | Opens `UI2WizardActivity` in new-scenario mode. |
| 14 | `VIEW_CLICKED` × 6 | `contentDescription="Wizard section: Usage Data"` → Inverters → PV System → Battery → Hot Water → EV | Phase 4A's parameterized semantics. Each tap expands one accordion section so the screenshot captures that section's controls. |
| 15 | `PRESSED_BACK` | — | Exit the wizard. Returns to Scenarios. |
| 16 | `VIEW_CLICKED` | `text="Leave"` | Defensive — only fires if the "Leave without saving?" dialog appears (it shouldn't because we didn't edit anything, but tolerates the case). A click whose selector isn't visible is a no-op in Robo. |

Robo records continuous screenshots while crawling, so the screenshot for each step is captured automatically — no explicit `SCREENSHOT` events needed in the script.

---

## How the FTL job runs it

The `robo` job in [`.github/workflows/ftl-android.yml`](../../.github/workflows/ftl-android.yml) runs in parallel with the existing instrumentation `ftl` job. Same WIF auth, same results bucket. The Robo job uses a single-device portrait matrix (`MediumPhone.arm`, API 34) because the script's purpose is the screenshot path, not cross-device code validation — the 4-device instrumentation job continues to provide that.

```bash
gcloud firebase test android run \
  --type robo \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --robo-script playstore/robo-scripts/walkthrough.json \
  --device "model=MediumPhone.arm,version=34,locale=en,orientation=portrait" \
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
| `text="Try with sample data"` | Renaming the empty-state button or the drawer menu item | [`UI2NavigationDrawer.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2NavigationDrawer.kt), [`UI2DashboardFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2DashboardFragment.kt) `EmptyDashboardSampleCard` |
| `text="Sample · First Run"` | Renaming the bundled sample scenario | [`SampleDataLoader.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/SampleDataLoader.kt) `SAMPLE_PREFIX`, [`app/src/main/assets/samples/sample_scenario.json`](../../app/src/main/assets/samples/sample_scenario.json) |
| `text="Scenarios" / "Comparisons" / "Directors"` | Renaming bottom-nav items | [`app/src/main/res/menu/bottom_nav_menu.xml`](../../app/src/main/res/menu/bottom_nav_menu.xml) |
| `contentDescription="View graphs"` | Removing the cd on the Explore Data graph icon | [`UI2DashboardFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2DashboardFragment.kt) (lines 413 / 653 at time of writing) |
| `contentDescription="Menu"` | Renaming/removing the drawer button cd | [`UI2WizardActivity.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2WizardActivity.kt:325) and other UI2 activities' menu IconButtons |
| `contentDescription="Wizard section: $title"` | Changing the title text of a wizard accordion, or renaming the cd format | [`UI2WizardActivity.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2WizardActivity.kt) `WizardAccordionSection` |
| `text="+ Create new"` | Renaming the Scenarios SectionAction | [`UI2SimulationsFragment.kt`](../../app/src/main/java/com/tfcode/comparetout/ui2/UI2SimulationsFragment.kt:176) |

If a step's selector goes stale, Robo will fail to find the element, time out on that step, and continue with random crawling. The pre-launch report will still complete — it just won't follow the scripted path past the failure point. Watch for shorter-than-expected reports as a signal that the script has drifted.

---

## Limits

- **Post-script random crawl is not disabled.** After the scripted steps finish, FTL Robo and Play Console pre-launch reports continue with their standard random crawl. That's actually useful — random crawl explores edges the script doesn't — but it does mean the artifact output isn't *only* the scripted walkthrough.
- **No assertions.** Robo scripts can't assert "the scenario list now has 1 entry". They can only drive UI actions. Correctness validation lives in the instrumentation `Smoke*Test` classes (see [`docs/firebase/README.md`](../firebase/README.md#test-coverage)).
- **PVGIS dependency.** Step 3 (the 120 s wait) assumes the live PVGIS HTTPS call succeeds. If `re.jrc.ec.europa.eu` is down or rate-limiting, the simulation chain won't complete, and downstream steps that depend on populated dashboard data will produce uninteresting screenshots (the dashboard's "needs attention" accordion instead of charts). Re-running typically resolves it.
- **First-run only.** The empty-state button only appears when `dataSourceInfo == null && !hasScenarios`. If a Play Console pre-launch slot somehow carries DB state across runs (it shouldn't — each pre-launch report installs the APK fresh), the script's step 2 will skip and downstream steps that depend on the sample scenario existing will need the user to have already loaded it. Watch first runs after a Play Console environment change.

---

## Related

- [`plans/roboscript/robo-plan.md`](../../plans/roboscript/robo-plan.md) — full Phase 4 (A/B/C/D) plan and implementation history.
- [`docs/firebase/README.md`](../firebase/README.md) — Firebase Test Lab setup, IAM bindings, troubleshooting.
- [`playstore/robo-scripts/walkthrough.json`](../../playstore/robo-scripts/walkthrough.json) — the script itself.
