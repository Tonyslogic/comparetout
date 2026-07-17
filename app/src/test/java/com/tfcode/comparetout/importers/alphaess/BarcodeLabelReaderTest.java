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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.EAN13Writer;

import org.junit.Test;

import java.util.Random;

/**
 * ZXing encodes as well as decodes, so these tests generate their own
 * Code-128 label fixtures (plans/source/alpha.md §2) — a synthetic inverter
 * label with the SN and CheckCode barcodes stacked like the real sticker —
 * and assert the reader round-trips and classifies them.
 */
public class BarcodeLabelReaderTest {

    private static final String SERIAL = "AL2002321010043";
    private static final String CHECK_CODE = "X4J7";

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    /** One barcode rendered into an ARGB pixel block. */
    private static int[] pixelsOf(BitMatrix matrix, int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? BLACK : WHITE;
            }
        }
        return pixels;
    }

    /** A white "label" canvas with the given barcode blocks stacked down it. */
    private static int[] compose(int canvasWidth, int canvasHeight,
                                 int barWidth, int barHeight, int[]... bars) {
        int[] canvas = new int[canvasWidth * canvasHeight];
        java.util.Arrays.fill(canvas, WHITE);
        int gap = (canvasHeight - bars.length * barHeight) / (bars.length + 1);
        int left = (canvasWidth - barWidth) / 2;
        for (int i = 0; i < bars.length; i++) {
            int top = gap + i * (barHeight + gap);
            for (int y = 0; y < barHeight; y++) {
                System.arraycopy(bars[i], y * barWidth,
                        canvas, (top + y) * canvasWidth + left, barWidth);
            }
        }
        return canvas;
    }

    /** Code-128 label canvas — the format the real sticker uses. */
    private static int[] label(int canvasWidth, int canvasHeight,
                               int barWidth, int barHeight, String... contents) {
        int[][] bars = new int[contents.length][];
        for (int i = 0; i < contents.length; i++) {
            bars[i] = pixelsOf(new Code128Writer().encode(
                    contents[i], BarcodeFormat.CODE_128, barWidth, barHeight),
                    barWidth, barHeight);
        }
        return compose(canvasWidth, canvasHeight, barWidth, barHeight, bars);
    }

    private static int[] rotate90(int[] pixels, int width, int height) {
        int[] rotated = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                rotated[x * height + (height - 1 - y)] = pixels[y * width + x];
            }
        }
        return rotated;
    }

    @Test
    public void twoBarcodeLabelClassifiesSerialAndCheckCode() {
        int[] pixels = label(600, 300, 500, 80, SERIAL, CHECK_CODE);
        BarcodeLabelReader.LabelScan scan = BarcodeLabelReader.scan(pixels, 600, 300);
        assertEquals(SERIAL, scan.serial);
        assertEquals(CHECK_CODE, scan.checkCode);
    }

    @Test
    public void classificationIsOrderIndependent() {
        // CheckCode barcode above the SN barcode — shape decides, not position.
        int[] pixels = label(600, 300, 500, 80, CHECK_CODE, SERIAL);
        BarcodeLabelReader.LabelScan scan = BarcodeLabelReader.scan(pixels, 600, 300);
        assertEquals(SERIAL, scan.serial);
        assertEquals(CHECK_CODE, scan.checkCode);
    }

    @Test
    public void rotatedLabelStillDecodes() {
        // Vertical label (portrait photo of a sideways sticker) — the reader's
        // 90° retry must catch it.
        int[] pixels = rotate90(label(600, 300, 500, 80, SERIAL, CHECK_CODE), 600, 300);
        BarcodeLabelReader.LabelScan scan = BarcodeLabelReader.scan(pixels, 300, 600);
        assertEquals(SERIAL, scan.serial);
        assertEquals(CHECK_CODE, scan.checkCode);
    }

    @Test
    public void singleBarcodeFillsOnlyItsField() {
        int[] serialOnly = label(600, 160, 500, 80, SERIAL);
        BarcodeLabelReader.LabelScan scan = BarcodeLabelReader.scan(serialOnly, 600, 160);
        assertEquals(SERIAL, scan.serial);
        assertNull(scan.checkCode);

        int[] checkOnly = label(600, 160, 500, 80, CHECK_CODE);
        scan = BarcodeLabelReader.scan(checkOnly, 600, 160);
        assertNull(scan.serial);
        assertEquals(CHECK_CODE, scan.checkCode);
    }

    @Test
    public void eanProductBarcodeIsIgnored() {
        // EAN/UPC (and ITF) are excluded from the decode formats: with
        // TRY_HARDER they phantom-decode photo noise into unrelated digit
        // strings (field-observed). A real EAN-13 in frame — a product
        // sticker near the label — must not fill either field.
        int[] ean = pixelsOf(new EAN13Writer().encode(
                "4006381333931", BarcodeFormat.EAN_13, 500, 80), 500, 80);
        BarcodeLabelReader.LabelScan scan = BarcodeLabelReader.scan(
                compose(600, 160, 500, 80, ean), 600, 160);
        assertTrue(scan.isEmpty());
    }

    @Test
    public void pureDigitDecodeNeverFillsSerial() {
        // AlphaESS SNs always contain letters; a digits-only decode (however
        // it got in) must not land in the serial field — and 13 digits is too
        // long for a check code.
        int[] pixels = label(600, 160, 500, 80, "1234567890123");
        assertTrue(BarcodeLabelReader.scan(pixels, 600, 160).isEmpty());
    }

    @Test
    public void noiseImageComesBackEmptyNotThrown() {
        Random rnd = new Random(42);
        int[] noise = new int[400 * 400];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = 0xFF000000 | rnd.nextInt(0x1000000);
        }
        BarcodeLabelReader.LabelScan scan = BarcodeLabelReader.scan(noise, 400, 400);
        assertTrue(scan.isEmpty());
    }
}
