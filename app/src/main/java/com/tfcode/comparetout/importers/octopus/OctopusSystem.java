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

package com.tfcode.comparetout.importers.octopus;

import com.tfcode.comparetout.importers.octopus.responses.AccountResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * One import meter point discovered from /accounts/, with its export sibling
 * (if any) so a single fetch fills both `buy` and `feed`. The list of these is
 * persisted as JSON in DataStore under the Octopus system-list key.
 *
 * All Octopus rows share the "Octopus-&lt;import MPAN&gt;" sysSn namespace in
 * alphaESSTransformedData — the prefix keeps the string-driven source
 * classification (see UI2CompareViewModel) from mistaking a numeric MPAN for
 * an ESBN MPRN.
 */
public class OctopusSystem {

    public static final String SYS_SN_PREFIX = "Octopus-";

    public String importMpan;
    public List<String> importSerials = new ArrayList<>();
    public String exportMpan;
    public List<String> exportSerials = new ArrayList<>();
    /** GSP region letter from the current agreement's tariff code, e.g. "C". */
    public String region;
    public String postcode;
    /** The current (open-ended) agreement's tariff code, if any. */
    public String currentTariffCode;

    public String getSysSn() {
        return SYS_SN_PREFIX + importMpan;
    }

    /**
     * Builds the system list from an account response: one entry per import
     * meter point, pairing the property's export meter point (where present).
     */
    public static List<OctopusSystem> fromAccount(AccountResponse account) {
        List<OctopusSystem> systems = new ArrayList<>();
        if (null == account || null == account.properties) return systems;
        for (AccountResponse.Property property : account.properties) {
            if (null == property.electricityMeterPoints) continue;
            AccountResponse.MeterPoint export = null;
            for (AccountResponse.MeterPoint mp : property.electricityMeterPoints) {
                if (mp.isExport) export = mp;
            }
            for (AccountResponse.MeterPoint mp : property.electricityMeterPoints) {
                if (mp.isExport) continue;
                OctopusSystem system = new OctopusSystem();
                system.importMpan = mp.mpan;
                system.postcode = property.postcode;
                if (null != mp.meters) for (AccountResponse.Meter meter : mp.meters) {
                    if (null != meter.serialNumber && !meter.serialNumber.isEmpty())
                        system.importSerials.add(meter.serialNumber);
                }
                if (null != export) {
                    system.exportMpan = export.mpan;
                    if (null != export.meters) for (AccountResponse.Meter meter : export.meters) {
                        if (null != meter.serialNumber && !meter.serialNumber.isEmpty())
                            system.exportSerials.add(meter.serialNumber);
                    }
                }
                if (null != mp.agreements) for (AccountResponse.Agreement agreement : mp.agreements) {
                    if (null == agreement.validTo && null != agreement.tariffCode) {
                        system.currentTariffCode = agreement.tariffCode;
                        system.region = regionFromTariffCode(agreement.tariffCode);
                    }
                }
                systems.add(system);
            }
        }
        return systems;
    }

    /** "E-1R-VAR-22-11-01-C" → "C"; null when the code has no region suffix. */
    public static String regionFromTariffCode(String tariffCode) {
        if (null == tariffCode) return null;
        int idx = tariffCode.lastIndexOf('-');
        if (idx < 0 || idx + 1 >= tariffCode.length()) return null;
        String suffix = tariffCode.substring(idx + 1);
        return suffix.length() == 1 && Character.isLetter(suffix.charAt(0)) ? suffix : null;
    }

    /** "E-1R-VAR-22-11-01-C" → "VAR-22-11-01" (the product code). */
    public static String productCodeFromTariffCode(String tariffCode) {
        if (null == tariffCode) return null;
        String code = tariffCode;
        int firstDash = code.indexOf('-');
        int secondDash = code.indexOf('-', firstDash + 1);
        if (secondDash > 0) code = code.substring(secondDash + 1);   // strip "E-1R-"
        int lastDash = code.lastIndexOf('-');
        // strip the single-letter region suffix, if present
        if (lastDash > 0 && lastDash == code.length() - 2
                && Character.isLetter(code.charAt(code.length() - 1))) {
            code = code.substring(0, lastDash);
        }
        return code;
    }
}
