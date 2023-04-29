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

package com.tfcode.comparetout.model.costings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SubTotals {
    private final HashMap<Double, Double> priceToSubTotal;

    public SubTotals() {
        this.priceToSubTotal = new HashMap<>();
    }

    public void addToPrice(Double price, Double charge){
        Double totalSoFar = priceToSubTotal.get(price);
        if (!(null == totalSoFar)) {
            totalSoFar += charge;
            priceToSubTotal.put(price, totalSoFar);
        }
        else priceToSubTotal.put(price, charge);
    }

    public List<Double> getPrices() {
        return new ArrayList<>(priceToSubTotal.keySet());
    }

    public Double getSubTotalForPrice(Double price) {
        return priceToSubTotal.get(price);
    }
}
