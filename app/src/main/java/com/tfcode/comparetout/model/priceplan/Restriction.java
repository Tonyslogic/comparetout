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

package com.tfcode.comparetout.model.priceplan;

import android.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class Restriction {
    public enum RestrictionType {
        annual("Annual"),
        monthly("Monthly"),
        bimonthly ("Bimonthly");

        private final String value;

        RestrictionType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static RestrictionType fromValue(String value) {
            for (RestrictionType restrictionType : RestrictionType.values()) {
                if (restrictionType.value.equals(value)) {
                    return restrictionType;
                }
            }
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }

    private RestrictionType periodicity;
    private final Map<String, Pair<Integer, Double>> restrictionEntries = new HashMap<>();

    public void addEntry(RestrictionType type, String cost, int kWhLimit, double revisedPrice) {
        periodicity = type;
        restrictionEntries.put(cost, new Pair<>(kWhLimit, revisedPrice));
    }

    public RestrictionType getPeriodicity() {
        return periodicity;
    }

    public Pair<Integer, Double> getRestrictionForCost(String cost) {
        return restrictionEntries.get(cost);
    }

    public Map<String, Pair<Integer, Double>> getRestrictionEntries() {
        return restrictionEntries;
    }
}
