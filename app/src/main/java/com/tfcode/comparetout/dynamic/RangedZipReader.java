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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads individual entries out of a remote zip via byte-range requests, so a
 * handful of small CSVs can be pulled from SEMOpx's ~115&nbsp;MB historical
 * bulk archive without ever downloading it: one ranged fetch for the
 * end-of-central-directory record, one for the central directory (~1.7&nbsp;MB
 * for ~7,800 entries), then one small ranged fetch per wanted entry.
 * <p>
 * Plain zip only (no ZIP64) — the SEMOpx archive is well inside those limits,
 * and the parser fails loudly if that ever changes. The fetch seam is an
 * interface so tests drive it from an in-memory byte array.
 */
public final class RangedZipReader {

    private RangedZipReader() {}

    /** Byte-range access to the archive (HTTP Range in production, byte[] in tests). */
    public interface ByteRangeFetcher {
        long length() throws IOException;
        /** Inclusive range, clamped by the caller to the archive length. */
        byte[] fetch(long start, long endInclusive) throws IOException;
    }

    /** One central-directory entry. */
    public static final class Entry {
        public final String name;
        public final int method;
        public final long compressedSize;
        public final long uncompressedSize;
        public final long localHeaderOffset;

        Entry(String name, int method, long compressedSize, long uncompressedSize,
              long localHeaderOffset) {
            this.name = name;
            this.method = method;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.localHeaderOffset = localHeaderOffset;
        }
    }

    private static final int EOCD_SIG = 0x06054b50;
    private static final int CEN_SIG = 0x02014b50;
    private static final int LOC_SIG = 0x04034b50;
    private static final int MAX_EOCD_SCAN = 22 + 65535;

    /** List the archive's entries from its central directory (two ranged fetches). */
    public static List<Entry> centralDirectory(ByteRangeFetcher fetcher) throws IOException {
        long length = fetcher.length();
        long scan = Math.min(length, MAX_EOCD_SCAN);
        byte[] tail = fetcher.fetch(length - scan, length - 1);
        int eocd = lastIndexOfSig(tail, EOCD_SIG);
        if (eocd < 0) throw new IOException("zip end-of-central-directory not found");
        int totalEntries = u16(tail, eocd + 10);
        long cdSize = u32(tail, eocd + 12);
        long cdOffset = u32(tail, eocd + 16);
        if (totalEntries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL)
            throw new IOException("ZIP64 archive not supported");

        byte[] cd = fetcher.fetch(cdOffset, cdOffset + cdSize - 1);
        List<Entry> entries = new ArrayList<>(totalEntries);
        int i = 0;
        while (i + 46 <= cd.length && (int) u32(cd, i) == CEN_SIG) {
            int method = u16(cd, i + 10);
            long csize = u32(cd, i + 20);
            long usize = u32(cd, i + 24);
            int nlen = u16(cd, i + 28);
            int elen = u16(cd, i + 30);
            int clen = u16(cd, i + 32);
            long lho = u32(cd, i + 42);
            String name = new String(cd, i + 46, nlen, java.nio.charset.StandardCharsets.UTF_8);
            entries.add(new Entry(name, method, csize, usize, lho));
            i += 46 + nlen + elen + clen;
        }
        return entries;
    }

    /** Fetch and decompress one entry (two small ranged fetches). */
    public static byte[] read(ByteRangeFetcher fetcher, Entry entry) throws IOException {
        // The local header's name/extra lengths may differ from the central
        // directory's, so read the fixed header first, then the exact data range.
        byte[] header = fetcher.fetch(entry.localHeaderOffset, entry.localHeaderOffset + 29);
        if ((int) u32(header, 0) != LOC_SIG)
            throw new IOException("bad local header for " + entry.name);
        int nlen = u16(header, 26);
        int elen = u16(header, 28);
        long dataStart = entry.localHeaderOffset + 30 + nlen + elen;
        byte[] data = fetcher.fetch(dataStart, dataStart + entry.compressedSize - 1);

        if (entry.method == 0) return data; // stored
        if (entry.method != 8) throw new IOException(
                "unsupported compression method " + entry.method + " for " + entry.name);
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    (int) Math.max(entry.uncompressedSize, 1024));
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && inflater.needsInput()) break;
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("corrupt deflate stream for " + entry.name, e);
        } finally {
            inflater.end();
        }
    }

    private static int lastIndexOfSig(byte[] data, int sig) {
        for (int i = data.length - 4; i >= 0; i--) {
            if ((int) u32(data, i) == sig) return i;
        }
        return -1;
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8)
                | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }
}
