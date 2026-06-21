# HP weather fixture — synthetic ERA5 2001 time-series

Phase 0 of the heat-pump plan (`plans/hp/plan.md`). A synthetic **2001** hourly outdoor-weather series in the
**raw CDS ERA5 time-series CSV** shape, used to prove the entire HP chain (physics → engine → grid alignment →
graphs) **offline** — no CDS account, no network. The same file also ships as an in-app asset so a user can
try the heat pump on "sample weather" before deciding whether to register with CDS.

## Files

| File | Purpose |
|---|---|
| `generate_synthetic_2001.py` | Deterministic generator (fixed seed → reproducible byte-for-byte). |
| `era5-timeseries-2001-synthetic.csv` | The fixture (this dir, for tests). 8,760 rows + header. |
| `../../../main/assets/hp-weather/era5-timeseries-2001-synthetic.csv` | Byte-identical shipped copy (in-app sample). |

Regenerate with: `python generate_synthetic_2001.py` (run from this directory), then copy the CSV to the
assets path above.

## Format — VERIFIED against CDS docs (2026-06-20)

The fixture mimics the **raw** output of the CDS point/time-series dataset so the live worker (Phase 6) and the
offline `FixtureWeatherProvider` (Phase 3) share one parser. Verified facts:

- **Dataset:** `reanalysis-era5-single-levels-timeseries` — point/time-series variant that returns **CSV**
  (not the gridded `reanalysis-era5-single-levels`, which returns NetCDF/GRIB). Coverage **1940→present**, so
  2001 is available. Hourly, 24 steps/day.
- **Variables (exact API names):** `2m_temperature`, `10m_u_component_of_wind`, `10m_v_component_of_wind`.
- **Units:** `t2m` in **Kelvin** (°C = K − 273.15); `u10`/`v10` in **m/s** (`windSpeed = √(u10² + v10²)`).
- **Time:** `valid_time` is **UTC**, ISO-8601 (`YYYY-MM-DDThh:mm:ss`).
- **2001 is not a leap year** → 365 × 24 = **8,760 rows**, matching the PVGIS `do2001` synthetic reference year
  so HP weather and PV generation share one grid.

### The exact CDS request this fixture stands in for

```python
client.retrieve("reanalysis-era5-single-levels-timeseries", {
    "variable": ["2m_temperature", "10m_u_component_of_wind", "10m_v_component_of_wind"],
    "location": {"latitude": 53.49, "longitude": -10.015},
    "date": ["2001-01-01/2001-12-31"],
    "data_format": "csv",
})
```

### CSV columns

```
valid_time,latitude,longitude,t2m,u10,v10
2001-01-01T00:00:00,53.490,-10.015,279.76,-7.44,-3.96
```

> **Header pinned by a real fetch (2026-06-21).** The live CDS time-series CSV header is
> `valid_time,u10,v10,t2m,latitude,longitude` with timestamps like `2022-01-01 00:00:00` (a **space**, not
> ISO `T`) — i.e. a **different column order** from this fixture and a different time separator. Rather than
> regenerate the fixture, `CsvWeatherProvider` was made **header-driven** (resolves `valid_time`/`t2m`/`u10`/
> `v10` by name) and timestamp-tolerant (accepts space or `T`), so it parses this fixture and live CDS output
> identically — the golden master stays byte-identical. The short names (`t2m`/`u10`/`v10`) were correct.

## What the data looks like (sanity-checked)

Synthesised for an Atlantic-coast Irish point (≈53.49, −10.015) from seasonal + diurnal sinusoids + seeded
noise — representative, not real:

- **Temperature:** annual mean ≈10 °C; monthly means Jan ≈5 °C → Jul ≈15 °C; diurnal min ≈03:00, max ≈15:00;
  extremes ≈ −1 °C … 21 °C.
- **Wind:** mean ≈6 m/s; windier in winter (Jan ≈8) than summer (Jul ≈4).

These ranges match Met Éireann maritime norms closely enough to exercise HDD redistribution, the wind
infiltration factor, and temperature-dependent COP. It is **not** a substitute for real CDS data in production —
it is a test/sample fixture only.
