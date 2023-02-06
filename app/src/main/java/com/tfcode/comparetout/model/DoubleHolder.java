package com.tfcode.comparetout.model;

import android.widget.EditText;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DoubleHolder {
    public List<Double> doubles;

    public DoubleHolder() {
        doubles = new ArrayList<>();
        for (int i = 0; i <= 24; i++) doubles.add(Double.valueOf(10));
    }

    public void update(Integer fromValue, Integer toValue, Double price) {
        for (int i = fromValue; i < toValue; i++) doubles.set(i, price);
        if (toValue == 24) doubles.set(toValue, price);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        Iterator<Double> itr = doubles.iterator();
        while (itr.hasNext()) {
            sb.append(itr.next().toString());
            if (itr.hasNext()) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
