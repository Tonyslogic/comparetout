# Firebase Test Lab — CI setup and operations guide

This project uses [Firebase Test Lab](https://firebase.google.com/docs/test-lab) (FTL) to run an Android instrumentation **smoke suite** on real and virtual Google-hosted devices before risky refactors are merged. The setup is intentionally minimal: manual trigger only (no automatic firing on push), Workload Identity Federation (no static credentials in the repo), free-tier-eligible device matrix.

This document covers everything you need to operate, troubleshoot, and extend the setup.

---

## TL;DR

| Aspect | Value |
|---|---|
| GCP project | `comparetout-ftl` (project number `911704177027`) |
| Trigger | GitHub Actions → **Firebase Test Lab** workflow → Run workflow |
| Auth | Workload Identity Federation (no JSON key in repo or secrets) |
| Devices | 4-device matrix: portrait phone, landscape phone, landscape tablet, older-API phone |
| Test classes | 5 `Smoke*Test` classes, ~5 min total runtime |
| Results | [Firebase console → Test Lab](https://console.firebase.google.com/project/comparetout-ftl/testlab) |
| Artifact retention | 30 days (auto-deleted from `gs://comparetout-ftl-results`) |

---

## How to trigger a run

1. **GitHub → Actions tab → Firebase Test Lab** (left sidebar)
2. Click **Run workflow** dropdown (top right)
3. Pick the branch you want to test (any branch in this repo works; the WIF binding doesn't restrict by branch)
4. Optional: override `device` (default `MediumPhone.arm`) or `version` (default `34`)
5. Click **Run workflow**

A run takes ~6 minutes wall-clock. While it's running, follow the **Actions** tab; once it's done, click through to the Firebase console for per-device videos and screenshots.

---

## End-to-end flow

Every run goes through these stages. None of them store long-lived credentials anywhere.

```
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 1. TRIGGER                                                            │
   │    "Run workflow" button OR (future) git push to enabled branch       │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 2. GITHUB ACTIONS BOOTSTRAPS                                          │
   │    Ubuntu runner spins up, checks out repo, sets up JDK 17 +          │
   │    Android SDK. No secrets read.                                      │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 3. GITHUB MINTS OIDC TOKEN                                            │
   │    The workflow's `permissions: id-token: write` causes GitHub's      │
   │    OIDC provider to issue a signed JWT for this specific workflow     │
   │    run, in this specific repo, on this specific branch. The token     │
   │    has a ~10-minute lifetime and never touches disk.                  │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 4. WIF EXCHANGE → SHORT-LIVED GCP TOKEN                               │
   │    google-github-actions/auth@v2 sends the OIDC JWT to                │
   │    sts.googleapis.com. GCP verifies the JWT against the WIF           │
   │    provider's attribute-condition (must come from                     │
   │    `Tonyslogic/*`), then mints a 1-hour access token scoped to the    │
   │    `github-actions-ftl` service account.                              │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 5. BUILD APKS                                                         │
   │    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest         │
   │    No FTL/GCP calls. Pure local build (~3 min).                       │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 6. UPLOAD + LAUNCH FTL MATRIX                                         │
   │    gcloud firebase test android run uploads APKs to                   │
   │    gs://comparetout-ftl-results and requests a multi-device run.      │
   │    Authentication is the token from step 4.                           │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 7. FTL EXECUTES                                                       │
   │    Google provisions the requested devices, installs the APKs,        │
   │    runs the Smoke* test classes. Runs in parallel across all          │
   │    devices in the matrix. ~5 min per device, parallelised.            │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 8. RESULTS                                                            │
   │    Pass/fail returned to gcloud, which exits accordingly.             │
   │    Artifacts (logcat, screenshots, video) stored in                   │
   │    gs://comparetout-ftl-results. Test history available in the        │
   │    Firebase console at console.firebase.google.com/project/           │
   │    comparetout-ftl/testlab.                                           │
   └──────────────────────────────────────────────────────────────────────┘
                                  ↓
   ┌──────────────────────────────────────────────────────────────────────┐
   │ 9. TEAR-DOWN                                                          │
   │    Runner discarded. GCP access token from step 4 expires within      │
   │    1 hour. No long-lived credential survives.                         │
   └──────────────────────────────────────────────────────────────────────┘
```

---

## Credentials — how it works without a JSON key

This setup deliberately avoids the most common Firebase-on-CI pattern (a service-account JSON key stored as a GitHub secret). That approach works but is a long-lived credential that needs rotating and can leak via accidentally-logged env vars, malicious dependencies, or compromised CI providers. We use **Workload Identity Federation** instead, which means the credential lives in memory for ~1 hour during one job and is impossible to exfiltrate as a static file.

### The trust chain

```
GitHub                       Google Cloud
─────────────────────────────────────────────────────────────────
  workflow run               WIF pool: github-pool
       │                        │
       │  OIDC JWT signed       │  provider: github-provider
       │  by GitHub (repo +     │  ├─ attribute-condition:
       │  branch + workflow,    │  │   repository_owner == 'Tonyslogic'
       │  ~10 min lifetime)     │  └─ attribute-mapping:
       │                        │      google.subject = sub
       │                        │      attribute.repository = repository
       │  ────────────────►     │
       │                        ↓
       │                     STS verifies the JWT against
       │                     the provider's condition.
       │                     If OK, mints a federated token.
       │                        │
       │                        ↓
       │                     Federated token is used to
       │                     IMPERSONATE the service account
       │                     'github-actions-ftl', because
       │                     the SA has a binding granting
       │                     roles/iam.workloadIdentityUser to
       │                     `principalSet://.../attribute.repository/
       │                                       Tonyslogic/comparetout`.
       │                        │
       │  ◄────────────────     │
       │  Access token          │
       │  (1 hour scope:        │
       │   cloud-platform)      │
       ↓
  gcloud uses the token for the duration of this one job.
  When the job ends, the token expires naturally.
```

### IAM bindings in summary

The service account `github-actions-ftl@comparetout-ftl.iam.gserviceaccount.com` has exactly these roles, scoped to project `comparetout-ftl` only:

| Role | Why |
|---|---|
| `roles/cloudtestservice.testAdmin` | Create and manage FTL test matrices |
| `roles/storage.objectAdmin` | Upload APKs and read results from the results bucket |
| `roles/iam.workloadIdentityUser` (on the SA itself, not project-wide) | Be impersonatable by the WIF principal set for `Tonyslogic/comparetout` |

It has no other permissions. If the (short-lived) access token ever leaked, an attacker would be limited to FTL operations and the results bucket within this single project — no billing, IAM, other projects, or production data.

### Where the trust is rooted

Three independent guards must all succeed for a successful authentication:

1. **GitHub OIDC** — only GitHub's identity provider can sign the JWT, and only for workflow runs that actually exist
2. **WIF provider condition** — `repository_owner == 'Tonyslogic'` rejects tokens for any repo not owned by you
3. **Service-account binding** — `principalSet://.../attribute.repository/Tonyslogic/comparetout` only allows that exact repo to impersonate the SA

The chain holds even if any one layer is compromised in isolation. There's no point along the path where a leaked secret would be sufficient to authenticate as the SA.

---

## Storage provisioning

FTL needs a project-owned bucket to receive APK uploads, logcat, screenshots, and videos. By default it creates one named like `test-lab-<random>` in whatever Google-owned project FTL routes you to — which means it's not in your project and your service account can't write to it. We override this by:

1. Creating a project-owned bucket: `gs://comparetout-ftl-results` (`us-central1`, uniform-bucket-level-access)
2. Granting the SA `roles/storage.objectAdmin` on it (covered by the project-level role)
3. Pointing FTL at it explicitly via `--results-bucket=comparetout-ftl-results` in the workflow

This is why the workflow's `gcloud firebase test android run` includes `--results-bucket`. Without it, FTL would fall back to its shared pool and our SA gets 403.

---

## Cleanup — automatic 30-day retention

The results bucket has a GCS lifecycle rule that deletes any object older than 30 days. Set once at bucket creation; runs automatically.

```json
{
  "rule": [
    {
      "action": {"type": "Delete"},
      "condition": {"age": 30}
    }
  ]
}
```

Applied via:

```bash
gcloud storage buckets update gs://comparetout-ftl-results --lifecycle-file=lifecycle.json
```

To inspect or modify retention:

```bash
gcloud storage buckets describe gs://comparetout-ftl-results --format="value(lifecycle_config)"
```

The Firebase console's **test history** records persist independently of the artifact files. After 30 days the videos/logcat are gone but the matrix metadata (test names, pass/fail, duration) stays visible — useful for trend analysis without keeping multi-GB artifact storage indefinitely.

Storage cost at current pace: a typical run produces ~50 MB across 4 devices. Twelve runs per month = ~600 MB held for 30 days × €0.02/GB/month ≈ €0.012/month. Negligible.

---

## Visualising results

Results are visible in two places:

### GitHub Actions (pass/fail summary)

- Actions tab → Firebase Test Lab → click the run
- The "Run smoke suite on Firebase Test Lab" step output shows per-test pass/fail and the matrix-result URL on the last line

### Firebase console (full artifacts)

- [https://console.firebase.google.com/project/comparetout-ftl/testlab](https://console.firebase.google.com/project/comparetout-ftl/testlab)
- One row per matrix; expand for per-device cards
- Per-device card shows: video playback, screenshots, logcat, test class results, performance metrics
- **Note:** the per-device card thumbnails only render correctly when the Firebase project has a registered Android app — added as `com.tfcode.comparetout.test` under Project Settings → Your apps. The registration is for visibility only; no `google-services.json` integration is required.

### Direct artifact access (GCS)

For automated artifact processing (e.g., screenshot diffing in future PRs):

```bash
gcloud storage ls gs://comparetout-ftl-results/
gcloud storage cp -r gs://comparetout-ftl-results/<run-id>/ ./local-artifacts/
```

---

## Robo walkthrough — parallel job

The workflow also runs a second job, `robo`, in parallel with the instrumentation `ftl` job. It drives a hand-written Robo script through the app's happy-path: dismiss the fresh-install dialogs → empty-state "Try with sample data" → wait for PVGIS+sim chain → expand every dashboard accordion (costing table, pie charts, KPIs, topology) → graphs screen → visit Compare and expand its Sources/Filter accordions → supplier-plan list → walk each wizard section on the populated sample scenario. The script expands each accordion before moving on, so the continuous screenshots capture rendered data instead of collapsed headers, and it sticks to reliably-present single-tap targets because Robo aborts the whole run on the first unfindable action. It runs on a phone (portrait) **and** a 10" tablet (landscape) so the results bucket carries Play-Store-ready captures in the orientation each form factor is listed in — the 4-device instrumentation matrix continues to provide cross-device *code* validation.

The same `walkthrough.json` is uploaded to Play Console once, after which every internal-testing release runs it as part of its automatic pre-launch report. See [`docs/playstore/robo-scripts.md`](../playstore/robo-scripts.md) for the script's narrative, Play Console upload procedure, and selector-maintenance guide.

---

## Test coverage

Five smoke-test classes run as a single FTL matrix. Each catches a different class of regression. All live under `app/src/androidTest/java/com/tfcode/comparetout/`.

### `SmokeLaunchTest` — ~5 s

Launches the app's `LAUNCHER` intent and verifies it survives `onCreate`. Cheapest smoke; catches a broken Application class or launcher Activity.

**Catches:** Hilt graph init failures, Application onCreate crashes, missing launcher activity.

### `SmokeActivityCoverageTest` — ~30 s

Cold-launches every top-level Activity that doesn't require pre-seeded Intent extras: `MainActivity`, `UI2MainActivity`, `UI2PricePlanListActivity`, `UI2DataSourceManagementActivity`, `UI2ImportExportActivity`, `UI2TimezoneActivity`, `ImportAlphaActivity`, `ImportESBNActivity`, `ImportHomeAssistantActivity`. Uses `ErrorCollector` so one failing activity doesn't block coverage of the rest — all failures reported together.

**Catches:** Activity-level regressions from refactors (removed Compose state that's still read; HiltViewModel constructor changes; layout-inflation errors). Catches every kind of bug introduced by the `AssignedValueIsNeverRead` cleanup (Phase 3b in the inspection plan).

### `SmokeRotationTest` — ~20 s

Drives `UI2MainActivity`, `UI2PricePlanListActivity`, and `UI2ImportExportActivity` through portrait → landscape → portrait rotation cycles using `UiDevice` from `androidx.test.uiautomator`. Each rotation triggers Activity recreation.

**Catches:** State-loss-on-rotation crashes (Compose `remember` without `rememberSaveable`, viewmodels held in non-survived fields, Bundle re-deserialisation bugs).

### `SmokeImportTest` — ~30 s

Two inline JSON fixtures (flat single-rate plan + multi-rate day/night plan) round-tripped through `JsonTools.createPricePlan` + `pricePlanDAO.addNewPricePlanWithDayRates` + `loadPricePlans`. Asserts the plan and its `DayRate` rows persist and read back correctly, including the `MinuteRateRange.fromHours` fallback.

**Catches:** JSON parser regressions, DAO schema breaks, the cost save/load roundtrip bug class that this codebase has historically had (the `insert` vs `add` empty-range bug, the broken hours-fallback in `createSinglePricePlanJsonObject`, etc).

### `SmokePVGISTest` — ~45 s

Live HTTP call to `re.jrc.ec.europa.eu/api/v5_2/seriescalc` for Dublin coordinates (lat 53.349, lon −6.260, slope 35°, azimuth 180°), parses the response into `PvGISData`, asserts ~8760 hourly rows. Marked `@LargeTest` so it can be excluded from fast suites if PVGIS is rate-limiting.

**Catches:** PVGIS API contract changes, `PvGISData` model drift, network-layer changes that break real HTTPS to JRC.

### Device matrix

Each run executes on these four virtual devices in parallel:

| # | Device | API | Orientation | Catches |
|---|---|---|---|---|
| 1 | `MediumPhone.arm` | 34 | Portrait | Baseline |
| 2 | `MediumPhone.arm` | 34 | Landscape | Layout in landscape |
| 3 | `MediumTablet.arm` | 34 | Landscape | Form-factor variance |
| 4 | `MediumPhone.arm` | 30 | Portrait | Older-API behavioural changes |

All four are quota-free. Total wall-clock ~6 min (parallel).

---

## Operations playbook

### Adding a new device to the matrix

Edit `.github/workflows/ftl-android.yml`. Find the `gcloud firebase test android run` block and add another `--device "model=...,version=...,..."` line. To find available device models, see:

```
gcloud firebase test android models list
```

### Adding a new test class

Drop the file under `app/src/androidTest/java/com/tfcode/comparetout/`. Then add a `--test-targets "class com.tfcode.comparetout.YourTest"` line in the workflow. Without that line, the test is built but not executed in the smoke run.

### Rotating credentials

Nothing to do. WIF mints fresh tokens on every job. The only long-lived artifact is the IAM binding itself, which is the binding by design.

### Disabling FTL temporarily

Edit `.github/workflows/ftl-android.yml` and set `on: workflow_dispatch:` to a non-existent trigger like `on: { workflow_dispatch: { branches: ['disabled'] } }`. Or delete the file (it's idempotent — recreating it doesn't require GCP re-setup).

### Tearing down the GCP side entirely

If you ever need to remove the project:

```bash
gcloud projects delete comparetout-ftl
```

This frees the project ID, billing link, all IAM bindings, the WIF pool, the results bucket, and any test history. Allow up to 30 days before the project ID becomes available again.

---

## Troubleshooting common errors

| Error | Likely cause |
|---|---|
| `unauthorized_client: The given credential is rejected by the attribute condition` | OIDC condition mismatch. Most often: repo-owner casing wrong (`tonyslogic` vs `Tonyslogic`) in the WIF provider's `attribute-condition`. Verify with `gcloud iam workload-identity-pools providers describe github-provider --location=global --workload-identity-pool=github-pool`. |
| `iam.serviceAccounts.getAccessToken denied on resource` | The service-account binding for the WIF principal set is missing or has the wrong repo path. Verify with `gcloud iam service-accounts get-iam-policy github-actions-ftl@comparetout-ftl.iam.gserviceaccount.com`. |
| `storage.objects.create denied` on a `test-lab-…` bucket | FTL fell back to a shared Google bucket because the workflow didn't pass `--results-bucket=comparetout-ftl-results`. Re-check the workflow YAML. |
| `Role roles/firebase.testLab.admin is not supported for this resource` | Role name typo (lowercase `firebase.testlab.admin` doesn't exist either; the actual role for FTL is `roles/cloudtestservice.testAdmin`). |
| Tests time out on FTL but pass locally | Increase `--timeout` in the workflow (default 10m). Tests that touch the network or sim worker may need 15–20m. |
| `Permission iam.workloadIdentityPools.create denied` | You're not project Owner. Check `gcloud auth list` and `gcloud config list account`. |

---

## Related files in this repo

| Path | Purpose |
|---|---|
| `.github/workflows/ftl-android.yml` | The workflow. Manual trigger only. |
| `app/src/androidTest/java/com/tfcode/comparetout/Smoke*Test.java` | The five smoke test classes. |
| `app/build.gradle` | `androidx.test.uiautomator` dependency for the rotation test. |

---

## Future work

Tracked as Phase B in the [inspection cleanup plan](../../plans/inspection-cleanup.md):

- **End-to-end test** with seeded data + PVGIS fetch + simulation worker + Compose UI assertions on the dashboard. Requires Hilt-test infra (`@HiltAndroidTest` + a test `Application`), `androidx.compose.ui:ui-test-junit4`, and WorkManager test driver. Roughly twice the LOC of the current smoke suite.
- **Screenshot-diff** between PR HEAD and main, using the artifacts the smoke runs already produce. Catches unintended visual regressions in Compose-heavy refactors.
- **Real-device runs** for one nightly tier, gated separately from the per-PR smoke. Use the same workflow but a different on-trigger (`schedule:`) and a different device matrix. Costs €0.05/min per real-device minute.
