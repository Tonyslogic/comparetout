package com.tfcode.comparetout.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IntHolder {
    public List<Integer> ints;

    public IntHolder() {
        ints = new ArrayList<>();
        for (int i = 0; i < 7; i++) ints.add(Integer.valueOf(i));
    }
}
