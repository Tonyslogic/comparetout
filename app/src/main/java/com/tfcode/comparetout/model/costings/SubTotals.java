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
