/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.esbn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.importers.TestSecrets;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ESBNHDFImportTest {

    private static ESBNHDFImport mImport;

    @BeforeClass
    public static void setup() {
        mImport = new ESBNHDFImport(
                TestSecrets.ESBN_USER,
                TestSecrets.ESBN_PASSWORD);
        mImport.setSelectedMPRN(TestSecrets.ESBN_MPRN);
    }

    @Test
    public void getMPRNs() {
        List<String> mprns = mImport.fetchMPRNs();
        assertFalse(mprns.isEmpty());
        assertTrue(mprns.contains(TestSecrets.ESBN_MPRN));
    }


    @Test
    public void badCredentials() {
        ESBNHDFImport bad = new ESBNHDFImport(
            "nobody@gamil.com",
            "B@DP@55word");
        List<String> mprns = bad.fetchMPRNs();
        assertTrue(mprns.isEmpty());
    }

    @Test
    public void getRangeFrom() {

        NavigableMap<LocalDateTime, Double> mImports = new TreeMap<>();
        NavigableMap<LocalDateTime, Double> mExports = new TreeMap<>();

        mImport.fetchSmartMeterDataFromDate("2024-01-01", (type, ldt, value) -> {
            switch (type) {
                case IMPORT: mImports.put(ldt, value); break;
                case EXPORT: mExports.put(ldt, value); break;
            }
        });
        assertFalse(mExports.isEmpty());
        assertFalse(mImports.isEmpty());
    }

    @Test
    public void getHDF() {

        NavigableMap<LocalDateTime, Double> mImports = new TreeMap<>();
        NavigableMap<LocalDateTime, Double> mExports = new TreeMap<>();

        mImport.fetchSmartMeterDataHDF((type, ldt, value) -> {
            switch (type) {
                case IMPORT: mImports.put(ldt, value); break;
                case EXPORT: mExports.put(ldt, value); break;
            }
        });
        assertFalse(mExports.isEmpty());
        assertFalse(mImports.isEmpty());
    }
}
