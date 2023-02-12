package com.tfcode.comparetout.model;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class IntHolderTest {

    @Test
    public void getCopy_does_not_modify_original() {
        IntHolder testObject = new IntHolder();
        List<Integer> copyObject = testObject.getCopyOfInts();
        copyObject.remove(1);
        assertTrue (testObject.ints.contains(1));
        assertFalse(copyObject.contains(1));
    }

    @Test
    public void toString_format() {
        IntHolder testObject = new IntHolder();
        assertEquals("[0, 1, 2, 3, 4, 5, 6]", testObject.toString());
    }
}