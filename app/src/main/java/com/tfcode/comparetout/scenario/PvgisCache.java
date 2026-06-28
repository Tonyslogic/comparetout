/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.scenario;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The on-disk cache for raw PVGIS {@code seriescalc} downloads, mirroring {@link HeatPumpWeatherCache}.
 *
 * <p>The UI2 fetch path ({@code PVGISDirectFetchWorker}) used to re-fetch from PVGIS on every panel and
 * write the result straight to {@code paneldata}. Because PVGIS now returns {@code P} (system power, W) with
 * {@code pvcalculation=1}, and {@code P} is <b>linear in peakpower</b> (the {@code (1−loss)} factor is also
 * linear, and the temperature derate is independent of peakpower), a single download taken at a
 * <b>reference peakpower of 1&nbsp;kWp</b> for a given (location, orientation, loss) can be re-scaled to any
 * array size: {@code panel_watts = P_per_kWp × (panelCount × panelkWp / 1000)}. So the cache key deliberately
 * <b>excludes the array size</b> — one file serves every panel that shares the location/orientation/loss.</p>
 *
 * <p>File name: {@code pvgis_{lat}_{lon}_{slope}_{az}_{loss}.json} in
 * {@code context.getFilesDir()/pvgis-cache}. lat/lon are formatted to 3 decimals with {@link Locale#ROOT}
 * (a {@code '.'} decimal) so the value lines up with {@code ScenarioDAO.countPanelDataForParameters}'
 * {@code ROUND(lat,3)} and never depends on the device locale.</p>
 */
public final class PvgisCache {

    private PvgisCache() {}

    /** Sub-directory of {@link Context#getFilesDir()} holding the cached PVGIS JSON downloads. */
    public static final String CACHE_DIR = "pvgis-cache";

    private static final String PREFIX = "pvgis_";
    private static final String SUFFIX = ".json";

    private static DecimalFormat latLon() {
        return new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    }

    /** The cache directory (created if absent). */
    public static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /**
     * The PV energy (kWh) in one 5-minute slot for an array of {@code peakKWp} kWp, given the cached
     * reference power {@code pWattsPerKWp} (PVGIS {@code P} for a 1 kWp system). PVGIS's {@code P} is exactly
     * linear in peakpower, so multiplying the per-kWp reference by the array size is an exact rescale; the
     * hour's average watts spread over the twelve slots and convert W→kW.
     */
    public static double intervalKwh(double pWattsPerKWp, double peakKWp) {
        return pWattsPerKWp * peakKWp / 12d / 1000d;
    }

    /** The deterministic file name for a (location, orientation, loss) — array size deliberately excluded. */
    public static String fileName(double latitude, double longitude, int slope, int azimuth, int lossPct) {
        DecimalFormat df = latLon();
        return PREFIX + df.format(latitude) + "_" + df.format(longitude)
                + "_" + slope + "_" + azimuth + "_" + lossPct + SUFFIX;
    }

    /** The deterministic cache file for a (location, orientation, loss). */
    public static File cacheFile(Context context, double latitude, double longitude,
                                 int slope, int azimuth, int lossPct) {
        return new File(cacheDir(context), fileName(latitude, longitude, slope, azimuth, lossPct));
    }

    /** True iff the exact (location, orientation, loss) JSON is already cached. */
    public static boolean cacheExists(Context context, double latitude, double longitude,
                                      int slope, int azimuth, int lossPct) {
        File f = cacheFile(context, latitude, longitude, slope, azimuth, lossPct);
        return f.exists() && f.length() > 0;
    }

    /** Every cached PVGIS JSON file (for the data-source-management cache view). */
    public static File[] listCacheFiles(Context context) {
        File[] files = cacheDir(context).listFiles(
                (dir, name) -> name.startsWith(PREFIX) && name.endsWith(SUFFIX));
        return files == null ? new File[0] : files;
    }

    /** The (lat, lon, slope, az, loss) a cache file name encodes, or {@code null} if it doesn't parse. */
    public static Key parse(String fileName) {
        if (fileName == null || !fileName.startsWith(PREFIX) || !fileName.endsWith(SUFFIX)) return null;
        String body = fileName.substring(PREFIX.length(), fileName.length() - SUFFIX.length());
        String[] f = body.split("_");
        if (f.length != 5) return null;
        try {
            return new Key(Double.parseDouble(f[0]), Double.parseDouble(f[1]),
                    Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Write {@code content} to {@code target} atomically (temp file + rename) so a crash can't half-write. */
    public static void writeAtomic(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        File tmp = new File(parent, target.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(content);
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!tmp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("could not write PVGIS cache file " + target.getName());
            }
        }
    }

    /** The decoded fields of a cache file name. */
    public static final class Key {
        public final double latitude;
        public final double longitude;
        public final int slope;
        public final int azimuth;
        public final int lossPct;

        public Key(double latitude, double longitude, int slope, int azimuth, int lossPct) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.slope = slope;
            this.azimuth = azimuth;
            this.lossPct = lossPct;
        }
    }
}
