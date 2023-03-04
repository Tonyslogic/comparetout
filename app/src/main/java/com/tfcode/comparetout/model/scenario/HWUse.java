package com.tfcode.comparetout.model.scenario;

import java.util.ArrayList;
import java.util.List;

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
