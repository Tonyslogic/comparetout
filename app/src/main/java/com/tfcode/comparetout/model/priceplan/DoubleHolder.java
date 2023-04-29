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

package com.tfcode.comparetout.model.priceplan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DoubleHolder {
    public List<Double> doubles;

    public DoubleHolder() {
        doubles = new ArrayList<>();
        for (int i = 0; i <= 24; i++) doubles.add(10.0);
    }

    public void update(Integer fromValue, Integer toValue, Double price) {
        for (int i = fromValue; i < toValue; i++) doubles.set(i, price);
        if (toValue == 24) doubles.set(toValue, price);
    }

    @NonNull
    public String toString() {
        return "[" + doubles.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }
}
