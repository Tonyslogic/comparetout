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

package com.tfcode.comparetout.dynamic;

import android.content.Context;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * On-disk cache of normalised wholesale price months (the {@code PvgisCache}
 * pattern): one JSON per {@code {marketId}_{year}_{month}} under
 * {@code filesDir/dynamic-price-cache}, so a cancelled or failed year-fetch
 * resumes from the first missing month instead of restarting a ~365-download
 * walk. Historical months never change once complete — fetch-once, with
 * explicit invalidation from the cache-management view only.
 * <p>
 * Core methods take the directory {@link File} directly (JVM-testable); the
 * {@link Context} overloads resolve the app cache dir.
 */
public final class DynamicPriceCache {

    private DynamicPriceCache() {}

    /** Sub-directory of {@link Context#getFilesDir()} holding the cached months. */
    public static final String CACHE_DIR = "dynamic-price-cache";

    private static final Gson GSON = new Gson();

    /** JSON payload of one cached month. */
    public static final class MonthChunk {
        public String marketId;
        public int year;
        public int month;
        /** Which publication supplied the month (report id / bulk file) + fetch date. */
        public String source;
        public long[] utcMillis;
        public double[] centsPerKwh;
        public int gapFilled;
    }

    public static File cacheDir(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static String fileName(String marketId, int year, int month) {
        return String.format(Locale.ROOT, "%s_%d_%02d.json", marketId, year, month);
    }

    public static File chunkFile(File cacheDir, String marketId, int year, int month) {
        return new File(cacheDir, fileName(marketId, year, month));
    }

    public static boolean exists(File cacheDir, String marketId, int year, int month) {
        File f = chunkFile(cacheDir, marketId, year, month);
        return f.exists() && f.length() > 0;
    }

    /** Load a cached month, or {@code null} when absent or unreadable. */
    public static MonthChunk load(File cacheDir, String marketId, int year, int month) {
        File f = chunkFile(cacheDir, marketId, year, month);
        if (!f.exists()) return null;
        try {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            MonthChunk chunk = GSON.fromJson(json, MonthChunk.class);
            if (null == chunk || null == chunk.utcMillis || null == chunk.centsPerKwh
                    || chunk.utcMillis.length != chunk.centsPerKwh.length) return null;
            return chunk;
        } catch (Exception e) {
            return null; // treat a corrupt chunk as a miss; it will be re-fetched
        }
    }

    /** Persist a month atomically (temp file + rename, the PvgisCache idiom). */
    public static void store(File cacheDir, MonthChunk chunk) throws IOException {
        File target = chunkFile(cacheDir, chunk.marketId, chunk.year, chunk.month);
        File parent = target.getParentFile();
        if (parent != null) //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        File tmp = new File(parent, target.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(GSON.toJson(chunk));
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!tmp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("could not write price cache file " + target.getName());
            }
        }
    }

    /** Every cached month file for a market (cache-management view; delete to invalidate). */
    public static File[] listCacheFiles(File cacheDir, String marketId) {
        File[] files = cacheDir.listFiles(
                (dir, name) -> name.startsWith(marketId + "_") && name.endsWith(".json"));
        return files == null ? new File[0] : files;
    }
}
