package com.tfcode.comparetout.model.scenario;

import java.util.ArrayList;
import java.util.List;

public class MonthlyDist {
    public List<Double> monthlyDist;

    public MonthlyDist() {
        monthlyDist = new ArrayList<>();
        double even = 100/12;
        for (int i = 0; i < 12; i++) monthlyDist.add(even);
    }
}
