# Dynamic Tariffs & Strategy Optimisation

How wholesale-tracking (dynamic) price plans work — where their prices come from, how they live in the
database, and how the strategy optimiser turns them into battery charge/export schedules, weather-aware
holds, and smart EV charging. This is the deliverable of `plans/dynamic/regional-dynamic-tariffs.md`
(status: `plans/dynamic/status.md`).

> Product note: the user-facing name is **Eco Power Optimiser**; `comparetout` / `TOUTC` are internal
> codenames only.

---

## 1. What a dynamic plan is

A conventional plan repeats a small set of `DayRate` rows across the year. A **dynamic** plan instead
carries the supplier's published **terms** (`DynamicTerms`, embedded in `PricePlan`, DB v16) and derives
its prices from a historical wholesale year:

```
import price (c or p / kWh) = clamp(wholesale × Multiplier + Adder, Floor, Cap)
export price (optional)     = wholesale × FeedMultiplier + FeedAdder     (unclamped)
```

- **Market** — which price feed the plan tracks (see §3).
- **Year** — a *backtest*: yearly costs are computed as if that year's prices repeated. Defaults to the
  last complete calendar year. Weekday patterns follow that year's real calendar.
- **Materialisation** — `DynamicTariffWorker` downloads the year's half-hourly prices and writes **365
  single-day BUY `DayRate` rows** (`startDate == endDate`); if the terms include a feed transform it also
  writes 365 SELL rows (`DayRate.rateType = 1`). Costing then works exactly as for any other plan via
  `RateLookup` — the engine has no idea the plan is dynamic.
- **Pending** — a plan with terms but no BUY rates yet. It shows a "pending" badge in the plan list
  (tap to retry the fetch) and is skipped by costing until it fills in.
- **Export is terms-only.** Market prices must not be redistributed, so JSON export emits the `Dynamic`
  block without rates; import re-fetches and regenerates locally. The JSON shape is documented in
  `model/priceplan/PricePlanJsonSchema.json`.

The generated day rates are never hand-editable — the terms card in the plan wizard is the only rate
mutation path (edit terms/year → regenerate → worker clobbers the plan by name and replaces its rates).

## 2. Ways to create one

| Path | Where | Notes |
|---|---|---|
| Generator pane | Plan list → Import sheet → "Dynamic tariff" | Regions with a market registered (IE). Enter year + terms; queues the worker; plan appears as pending, then materialises. |
| Plan wizard | New/edit plan → "Dynamic tariff" section → "Use dynamic terms" | Converts the plan: Rates/Restrictions sections swap for the Dynamic terms card. Reversible until prices are fetched. "Fetch prices & generate" queues the worker. |
| Octopus browser (GB) | Plan list → Import sheet → Octopus tariffs | Agile tariffs queue automatically as terms-only plans (×1 +0 — Agile publishes final retail prices). |
| JSON import | Paste/import a plan file with a `Dynamic` block and no `Rates` | Arrives pending, materialises in the background. |

Progress shows as a notification; a first fetch for a year takes minutes, later generates reuse the
on-device month cache (`DynamicPriceCache`).

## 3. Markets & sources (`dynamic/`)

`DynamicRateSources` maps a market id to a `HistoricalRateSource`:

| Market id | Source | Coverage / notes |
|---|---|---|
| `ISEM-DAM` | `SemopxRateSource` — SEMOpx public reports (I-SEM day-ahead, IE) | 2018-10 onward; prices fetched on-device, AS-IS, never redistributed. Known hole Oct-2024→Jul-2025 in one report series (follow-on: Lookback2 xlsx consumer). |
| `GB-AGILE-<A..P>` | `OctopusAgileRateSource` — Octopus public API | The GSP region letter is embedded in the market id. Deep history via the closed AGILE-18-02-21 product. No auth needed. |

Both normalise through `SeriesNormaliser` into half-hourly month chunks with gap-filling (the plan's
`Reference` records provenance and gap counts). Registration for the generator pane is per-region:
`region/RegionProfile.kt → dynamicMarkets` (GB's list is deliberately empty — Agile enters via the
Octopus pane, which knows the user's region).

## 4. The strategy optimiser (`dynamic/strategy/`)

Entry point: scenario card menu → **"Optimise for dynamic tariff"** (shown only for scenarios with a
battery). The dialog picks a materialised dynamic plan + a strategy, then
`StrategyScenarioGenerator.generateBlocking`:

1. **Copies the scenario** (shared panels/load-profile, everything else cloned) as
   `"<base> ⚡ <strategy> (<year>)"`. The original is never modified.
2. Walks the plan's year one day at a time (`StrategyYearRunner`), giving the strategy each day's 48
   half-hour buy/sell prices, the estimated half-hourly load (mirrors `GenerateMissingLoadDataWorker`),
   and the battery's carried state of charge.
3. Converts the per-day decisions into coalesced `LoadShift` (charge-from-grid) and `DischargeToGrid`
   rows (`ScheduleEmitter`) with single-day-precision date windows, and inserts the copy
   (clobber-by-name, so **generating again replaces the previous ⚡ copy**). The readiness matrix then
   sims + costs it automatically.

Deleting a ⚡ scenario is safe — shared components survive (orphan-based delete).

### Strategies

- **Threshold** (`ThresholdStrategy`) — two fixed price lines: charge every half-hour where the buy
  price is below X; export where the sell price is above Y *and* beats the day's cheapest charge cost
  plus losses plus the minimum spread. Predictable, but a fixed line misses unusually cheap/dear days.
- **Rank-N** (`RankNStrategy`) — no fixed lines: each day the N cheapest half-hours charge (plus every
  negative-price half-hour), and up to M dearest half-hours export, capped by how much energy the
  battery can actually hold/deliver, and only when the sell price beats the average charge cost plus
  losses plus the spread.
- **Minimum spread** — both strategies require export to earn at least this margin per kWh over the
  stored energy's cost after round-trip losses (`BatterySpec.breakEvenSellCents`). Raise it to cycle
  the battery less.

Battery figures come from the scenario's battery: capacity, discharge floor, charge/discharge rate per
half-hour (engine units: `Battery.maxCharge/maxDischarge` are kWh per 5-minute interval), round-trip
loss, and the load profile's grid-export cap. A degradation cost per kWh exists in `BatterySpec` but is
currently 0 (follow-on: expose it in the dialog).

### Weather-aware layer (optional, `WeatherAwareStrategy` + `LayerBOutlook`)

Wind forecasts are a leading indicator of I-SEM prices (calm ⇒ dear). When enabled, the base strategy is
wrapped: it **holds charge instead of exporting** when the confidence-weighted expected peak price in
the next ~10 days beats today's best export price. Expected prices come from a per-month wind→price
quantile calibration (`WindPriceCalibration`) built from the plan's own materialised year; confidence
decays 0.85/day and is halved when the cached weather year ≠ plan year.

Weather is **reused, never fetched**: it reads the CDS/ERA5 cache a heat-pump scenario already
downloaded (`filesDir/hp-weather-cache/cds_*.csv`). No cached weather ⇒ the option fails honestly.

### EV smart charging (optional, `EvSmartChargePlanner`)

Keeps each EV charging session's energy identical but moves it into the cheapest half-hours of the
plug-in window (arrival 18:00 → departure 08:00 next morning, currently fixed — follow-on: user
parameters). The base scenario's `EVCharge` rows define the sessions (days, draw, duration); the
planner replaces them in the ⚡ copy with single-purpose date+minute windows named "⚡ EV …".

Note the end-hour convention: engine `LoadShift`/`DischargeToGrid` *include* their end hour; HW/EV
components *exclude* it (`getEffectiveEndMinute()`), and the strategy/emitter code preserves both.

## 5. Known limits (recorded in `plans/dynamic/status.md`)

- SEMOpx has a data hole (Oct-2024→Jul-2025) pending the Lookback2 xlsx consumer.
- Weather awareness needs previously fetched CDS weather; it never fetches on its own.
- EV window is fixed 18:00→08:00; hot-water diversion is not optimised; degradation cost is 0.
- IE generator pane defaults are placeholders — enter the real supplier terms.
- Octopus Tracker products and a hindsight (perfect-foresight) benchmark are out of scope.

## 6. Code map

| Area | Where |
|---|---|
| Terms model / JSON | `model/priceplan/DynamicTerms`, `model/json/priceplan/DynamicTermsJson`, schema `model/priceplan/PricePlanJsonSchema.json` |
| Fetch & materialise | `dynamic/` — `DynamicTariffWorker`, `DynamicRateSources`, `SemopxRateSource`, `OctopusAgileRateSource`, `SeriesNormaliser`, `DynamicPriceCache` |
| Strategy engine (pure JVM) | `dynamic/strategy/` — `DispatchStrategy`, `ThresholdStrategy`, `RankNStrategy`, `SocForwardModel`, `StrategyYearRunner`, `ScheduleEmitter`, `WeatherAwareStrategy`, `WindPriceCalibration`, `LayerBOutlook`, `EvSmartChargePlanner` |
| UI | `ui2/DynamicTariffPlans.kt`, `ui2/StrategyScenarioGenerator.kt`, dialog in `ui2/UI2SimulationsFragment.kt`, generator pane in `ui2/UI2PricePlanListActivity.kt`, terms card in `ui2/UI2PricePlanWizardActivity.kt` |
| Tests | `ThresholdStrategyTest`, `RankNStrategyTest`, `StrategyYearRunnerTest`, `ScheduleEmitterTest`, `WindPriceCalibrationTest`, `LayerBOutlookTest`, `WeatherAwareStrategyTest`, `EvSmartChargePlannerTest`, `OctopusAgileRateSourceTest`, `ComponentDateWindowTest` |
