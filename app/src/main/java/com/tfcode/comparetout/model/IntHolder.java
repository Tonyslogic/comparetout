/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.model;

import androidx.annotation.NonNull;

import com.tfcode.comparetout.model.scenario.MonthHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Container class for managing collections of integer values.
 * 
 * This class provides a wrapper around a list of integers with additional
 * functionality for day-of-week scheduling and time-based configurations.
 * It is primarily used in pricing and scheduling contexts where specific
 * integer sets need to be maintained and compared.
 * 
 * The class initializes with a default set of values (0-6) representing
 * days of the week, making it suitable for schedule definitions. It provides
 * safe access methods and equality comparison that considers content rather
 * than order.
 * 
 * Key features:
 * - Defensive copying to prevent external modification
 * - Order-independent equality comparison  
 * - String representation for debugging and display
 * - Integration with day-of-week scheduling systems
 */
public class IntHolder {
    /** List of integer values managed by this holder */
    public List<Integer> ints;

    /**
     * Default constructor initializing with day-of-week values.
     * 
     * Creates an IntHolder with integer values 0-6, representing the seven
     * days of the week. This default initialization makes the class immediately
     * useful for scheduling applications without requiring explicit setup.
     */
    public IntHolder() {
        ints = new ArrayList<>();
        for (int i = 0; i < 7; i++) ints.add(i);
    }

    /**
     * Returns a defensive copy of the integer list.
     * 
     * This method provides safe access to the internal integer list by
     * returning a copy rather than the original list. This prevents
     * external code from inadvertently modifying the internal state
     * while still allowing read access to the values.
     * 
     * @return A new ArrayList containing copies of all integer values
     */
    public List<Integer> getCopyOfInts(){
        return new ArrayList<>(ints);
    }

    /**
     * Returns a string representation of the integer list.
     * 
     * Creates a comma-separated string representation of all integers
     * enclosed in brackets, suitable for debugging, logging, and display
     * purposes. The format matches standard array notation.
     * 
     * @return String representation in format "[1, 2, 3]"
     */
    @NonNull
    public String toString() {
        return "[" + ints.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }

    /**
     * Compares this IntHolder with another object for equality.
     * 
     * Two IntHolder objects are considered equal if they contain the same
     * integer values, regardless of order. This order-independent comparison
     * is achieved by sorting both lists before comparison, making it suitable
     * for set-like operations where order doesn't matter.
     * 
     * @param o The object to compare with this IntHolder
     * @return true if both objects contain the same integer values, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (((IntHolder) o).ints.size() != ints.size()) return false;

        // Perform order-independent comparison by sorting both lists
        ArrayList<Integer> one = new ArrayList<>(((IntHolder) o).ints);
        ArrayList<Integer> two = new ArrayList<>(ints);
        Collections.sort(one);
        Collections.sort(two);

        return one.equals(two);
    }
}
