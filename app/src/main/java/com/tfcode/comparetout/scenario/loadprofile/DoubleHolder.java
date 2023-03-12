package com.tfcode.comparetout.scenario.loadprofile;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DoubleHolder {
    public List<Double> doubles;

    public DoubleHolder() {
        doubles = new ArrayList<>();
        for (int i = 0; i <= 24; i++) doubles.add(100/24.0);
    }

    public void update(Integer fromValue, Integer toValue, Double price) {
        for (int i = fromValue; i < toValue; i++) doubles.set(i, price);
        if (toValue == 24) doubles.set(toValue-1, price);
    }

    @NonNull
    public String toString() {
        return "[" + doubles.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }
}
