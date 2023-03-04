package com.tfcode.comparetout.model;

import static org.junit.Assert.*;

import com.tfcode.comparetout.model.priceplan.DoubleHolder;

import org.junit.Test;

public class DoubleHolderTest {

    @Test
    public void testToString() {
        DoubleHolder testObject = new DoubleHolder();
        assertEquals("[10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, " +
                "10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0]", testObject.toString());
    }

    @Test
    public void update() {
        DoubleHolder testObject = new DoubleHolder();
        testObject.update(10, 12, 22.2);
        assertEquals("[10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 22.2, 22.2, " +
                "10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0]", testObject.toString());

    }
}