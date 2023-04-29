package com.tfcode.comparetout.model.scenario;

import java.util.ArrayList;
import java.util.List;

public class DOWDist {
    public List<Double> dowDist;

    public DOWDist() {
        dowDist = new ArrayList<>();
        double even = 100/7D;
        for (int i = 0; i < 7; i++) dowDist.add(even);
    }
}
