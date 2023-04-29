package com.tfcode.comparetout.model.scenario;

import java.util.ArrayList;
import java.util.List;

public class HourlyDist {
    public List<Double> dist;

    public HourlyDist() {
        dist = new ArrayList<>();
        double even = 100/24D;
        for (int i = 0; i < 24; i++) dist.add(even);
    }
}
