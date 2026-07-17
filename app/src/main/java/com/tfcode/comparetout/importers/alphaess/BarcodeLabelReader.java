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

package com.tfcode.comparetout.importers.alphaess;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decodes the SN and CheckCode barcodes from a photo of an AlphaESS inverter
 * label (plans/source/alpha.md §2). Pure JVM on purpose — it takes an ARGB
 * pixel array rather than an android.graphics.Bitmap so the decode +
 * classification logic unit-tests without Robolectric; the UI layer does the
 * Bitmap→pixels (and ≤2048 px downscale) conversion.
 *
 * <p>The label carries 1-D barcodes (Code-128 family) next to their printed
 * text. Both target fields stay editable in the UI, so a wrong classification
 * here is a one-tap fix, not a dead end.
 */
public final class BarcodeLabelReader {

    /** What a scan found — either field may be null (not decoded). */
    public static final class LabelScan {
        @Nullable public final String serial;
        @Nullable public final String checkCode;

        LabelScan(@Nullable String serial, @Nullable String checkCode) {
            this.serial = serial;
            this.checkCode = checkCode;
        }

        public boolean isEmpty() {
            return serial == null && checkCode == null;
        }
    }

    // AlphaESS SNs are long alphanumerics (~15 chars, typically "AL…") and
    // always contain letters — requiring one rejects the pure-digit strings
    // that phantom decodes produce. The CheckCode is the short one. The
    // length bands deliberately don't touch so a two-barcode label can't
    // classify both onto one field.
    private static final Pattern SERIAL_SHAPE =
            Pattern.compile("(?=.*[A-Za-z])[A-Za-z0-9-]{12,}");
    private static final Pattern CHECK_CODE_SHAPE = Pattern.compile("[A-Za-z0-9]{3,10}");

    private static final Map<DecodeHintType, Object> HINTS = buildHints();

    private static Map<DecodeHintType, Object> buildHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        // Code-128 family only — the formats the label actually uses. EAN/UPC
        // and ITF are deliberately absent: with TRY_HARDER they phantom-decode
        // photo noise into unrelated digit strings (field-observed 2026-07-17),
        // and their weak/absent checksums can't stop it.
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93));
        return hints;
    }

    private BarcodeLabelReader() {
    }

    /**
     * Scan [argbPixels] (row-major, width×height) for label barcodes and
     * classify them into serial/check-code. Tries the image as-is, then
     * rotated 90° (vertical labels — TRY_HARDER only covers 180° flips).
     * Never throws on "nothing found": that comes back as an empty scan.
     */
    @NonNull
    public static LabelScan scan(int[] argbPixels, int width, int height) {
        Set<String> texts = new LinkedHashSet<>(decode(argbPixels, width, height));
        if (texts.isEmpty()) {
            texts.addAll(decode(rotate90(argbPixels, width, height), height, width));
        }
        return classify(texts);
    }

    private static List<String> decode(int[] pixels, int width, int height) {
        List<String> texts = new ArrayList<>();
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(width, height, pixels)));
        try {
            GenericMultipleBarcodeReader reader =
                    new GenericMultipleBarcodeReader(new MultiFormatReader());
            for (Result r : reader.decodeMultiple(bitmap, HINTS)) {
                String text = r.getText() == null ? "" : r.getText().trim();
                if (!text.isEmpty()) texts.add(text);
            }
        } catch (Exception nothingFound) {
            // NotFoundException — plus any decoder hiccup on noise input.
        }
        return texts;
    }

    /** Rotate 90° clockwise: (x,y) → (height-1-y, x) in a height×width image. */
    private static int[] rotate90(int[] pixels, int width, int height) {
        int[] rotated = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotated[x * height + (height - 1 - y)] = pixels[y * width + x];
            }
        }
        return rotated;
    }

    @NonNull
    private static LabelScan classify(Set<String> texts) {
        String serial = null;
        String checkCode = null;
        for (String text : texts) {
            if (SERIAL_SHAPE.matcher(text).matches()) {
                // Prefer the conventional "AL…" serial if several qualify.
                if (serial == null || (text.toUpperCase().startsWith("AL")
                        && !serial.toUpperCase().startsWith("AL"))) {
                    serial = text;
                }
            } else if (CHECK_CODE_SHAPE.matcher(text).matches()) {
                if (checkCode == null || text.length() < checkCode.length()) {
                    checkCode = text;
                }
            }
        }
        return new LabelScan(serial, checkCode);
    }
}
