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

package com.tfcode.comparetout.model;

import androidx.annotation.NonNull;

import com.tfcode.comparetout.model.scenario.MonthHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class IntHolder {
    public List<Integer> ints;

    public IntHolder() {
        ints = new ArrayList<>();
        for (int i = 0; i < 7; i++) ints.add(i);
    }

    public List<Integer> getCopyOfInts(){
        return new ArrayList<>(ints);
    }

    @NonNull
    public String toString() {
        return "[" + ints.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (((IntHolder) o).ints.size() != ints.size()) return false;

        ArrayList<Integer> one = new ArrayList<>(((IntHolder) o).ints);
        ArrayList<Integer> two = new ArrayList<>(ints);
        Collections.sort(one);
        Collections.sort(two);

        return one.equals(two);
    }
}
