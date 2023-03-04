package com.tfcode.comparetout.model.scenario;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MonthHolder {
    public List<Integer> months;

    public MonthHolder() {
        months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) months.add(i);
    }

    @NonNull
    public String toString() {
        return "[" + months.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }
}
