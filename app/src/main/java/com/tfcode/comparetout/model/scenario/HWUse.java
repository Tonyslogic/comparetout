/*
 * Copyright (c) 2023. Tony Finnerty
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

package com.tfcode.comparetout.model.scenario;

import java.util.ArrayList;

public class HWUse {
    private ArrayList<ArrayList<Double>> usage;

    public HWUse() {
        usage = new ArrayList<>();
        addUse(8d,75d);
        addUse(14d,10d);
        addUse(20d, 15d);
    }

    public void addUse(double hr, double percent) {
        ArrayList<Double> useAt = new ArrayList<>();
        useAt.add(hr);
        useAt.add(percent);
        usage.add(useAt);
    }

    public ArrayList<ArrayList<Double>> getUsage() {
        return usage;
    }

    public void setUsage(ArrayList<ArrayList<Double>> usage) {
        this.usage = usage;
    }
}
