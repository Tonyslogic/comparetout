package com.tfcode.comparetout.model;

import java.util.ArrayList;
import java.util.List;

public class DoubleHolder {
    public List<Double> doubles;

    public DoubleHolder() {
        doubles = new ArrayList<>();
        for (int i = 0; i <= 24; i++) doubles.add(Double.valueOf(10));
    }
}
