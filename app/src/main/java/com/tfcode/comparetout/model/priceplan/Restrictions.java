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

import java.util.ArrayList;
import java.util.List;

public class Restrictions {
    private boolean active = false;
    private List<Restriction> restrictions = new ArrayList<>();

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Restriction> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(List<Restriction> restrictions) {
        this.restrictions = restrictions;
    }

    public Restriction getRestrictionForCost(String rate) {
        Restriction ret = null;
        for (Restriction r : restrictions) {
            Pair<Integer, Double> match = r.getRestrictionForCost(rate);
            if (!(match == null)) {
                ret = r;
                break;
            }
        }
        return ret;
    }
}
