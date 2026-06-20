#!/usr/bin/env python3
"""
Generate a synthetic 2001 hourly weather series in the *raw* CDS ERA5 time-series
CSV shape, for the heat-pump simulation component (plans/hp).

Why synthetic: it lets the whole HP chain (physics -> engine -> alignment -> graphs)
be proven OFFLINE, with no CDS account, and ships in-app as a "sample weather"
option so a user can try the heat pump before deciding to register with CDS.

Format target (VERIFIED against CDS docs, 2026-06-20):
  dataset : reanalysis-era5-single-levels-timeseries   (point/time-series, returns CSV)
  request : {
              "variable": ["2m_temperature",
                           "10m_u_component_of_wind",
                           "10m_v_component_of_wind"],
              "location": {"latitude": 53.49, "longitude": -10.015},
              "date": ["2001-01-01/2001-12-31"],
              "data_format": "csv",
            }
  columns : valid_time (ISO-8601, UTC), latitude, longitude,
            t2m (Kelvin), u10 (m/s), v10 (m/s)

NOTE: the exact CSV header spelling / column order from the live CDS endpoint is the
one detail NOT verifiable from the docs without an account; it is pinned on the first
real fetch in Phase 6 and the parser adjusted if needed. This generator uses the
ERA5 netCDF short names (t2m / u10 / v10), which is the documented convention.

2001 is NOT a leap year -> 365 days x 24 h = 8760 rows (matches the PVGIS "do2001"
synthetic reference year, so HP weather and PV generation share one grid).

Deterministic (fixed seed) so the committed fixture is reproducible byte-for-byte.
"""

import csv
import math
import random
from datetime import datetime, timedelta, timezone

SEED = 20010101
LAT = 53.49        # Atlantic coast of Ireland (matches the design's worked location)
LON = -10.015
KELVIN = 273.15

# Temperature model (degrees C, converted to Kelvin on output)
T_MEAN = 10.0          # annual mean ~10 C (maritime Irish climate)
T_SEASONAL_AMP = 5.0   # Jan ~5 C, Jul ~15 C
T_DIURNAL_AMP = 3.0    # ~+/-3 C across the day
T_COLD_DOY = 20        # coldest around 20 Jan
T_WARM_HOUR = 15       # warmest around 15:00
T_NOISE_SD = 1.2

# Wind model (m/s)
W_MEAN = 6.0           # windy Atlantic coast
W_SEASONAL_AMP = 2.0   # windier in winter (~8), calmer in summer (~4)
W_NOISE_SD = 1.5
W_FLOOR = 0.4
W_PREVAILING_DEG = 225.0   # south-westerly prevailing wind
W_DIR_NOISE_SD = 40.0


def main():
    rng = random.Random(SEED)
    start = datetime(2001, 1, 1, 0, 0, 0, tzinfo=timezone.utc)
    rows = []
    for h in range(365 * 24):
        ts = start + timedelta(hours=h)
        doy = ts.timetuple().tm_yday          # 1..365
        hour = ts.hour

        # temperature (C)
        #   seasonal: -cos peaks-negative at the COLDEST day (T_COLD_DOY) -> minimum in Jan
        #   diurnal:  +cos peaks-positive at the WARMEST hour (T_WARM_HOUR) -> maximum mid-afternoon
        seasonal = -math.cos(2 * math.pi * (doy - T_COLD_DOY) / 365.0) * T_SEASONAL_AMP
        diurnal = math.cos(2 * math.pi * (hour - T_WARM_HOUR) / 24.0) * T_DIURNAL_AMP
        temp_c = T_MEAN + seasonal + diurnal + rng.gauss(0, T_NOISE_SD)
        t2m_k = temp_c + KELVIN

        # wind speed (m/s): windier in winter
        w_seasonal = math.cos(2 * math.pi * (doy - T_COLD_DOY) / 365.0) * W_SEASONAL_AMP
        speed = max(W_FLOOR, W_MEAN + w_seasonal + rng.gauss(0, W_NOISE_SD))

        # split into u/v via a noisy prevailing direction (downstream uses magnitude)
        theta = math.radians(W_PREVAILING_DEG + rng.gauss(0, W_DIR_NOISE_SD))
        u10 = speed * math.cos(theta)
        v10 = speed * math.sin(theta)

        rows.append((
            ts.strftime("%Y-%m-%dT%H:%M:%S"),
            f"{LAT:.3f}", f"{LON:.3f}",
            f"{t2m_k:.2f}", f"{u10:.2f}", f"{v10:.2f}",
        ))

    out = "era5-timeseries-2001-synthetic.csv"
    with open(out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["valid_time", "latitude", "longitude", "t2m", "u10", "v10"])
        w.writerows(rows)
    print(f"wrote {len(rows)} rows to {out}")


if __name__ == "__main__":
    main()
