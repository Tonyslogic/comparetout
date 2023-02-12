package com.tfcode.comparetout.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class IntHolder {
    public List<Integer> ints;

    public IntHolder() {
        ints = new ArrayList<>();
        for (int i = 0; i < 7; i++) ints.add(i);
    }

    public List<Integer> getCopyOfInts(){
        return new ArrayList<>(ints);
    }

    @NonNull
    public String toString() {
        return "[" + ints.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }
}
