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

package com.tfcode.comparetout.model.priceplan;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Container class for managing hourly double values in price plan configurations.
 * 
 * This class provides a wrapper around a list of double values representing
 * hourly data throughout a 24-hour period (plus one extra value at index 24).
 * It is specifically designed for time-of-use pricing where different rates
 * apply at different hours of the day.
 * 
 * The class initializes with a default value (10.0) for all hours and provides
 * an update mechanism to set specific price ranges. This design supports the
 * common use case where most hours have a standard rate with exceptions for
 * peak or off-peak periods.
 * 
 * Key features:
 * - 25 slots (0-24) to handle hour boundary conditions
 * - Range-based updates for setting prices across multiple hours
 * - Default initialization with standard rates
 * - String representation for debugging and display
 */
public class DoubleHolder {
    /** List of double values representing hourly data (0-24 hours) */
    public List<Double> doubles;

    /**
     * Default constructor initializing with standard values for all hours.
     * 
     * Creates a DoubleHolder with 25 slots (hours 0-24) all initialized to
     * the default value of 10.0. The extra slot at index 24 handles boundary
     * conditions when setting rates that end exactly at midnight.
     */
    public DoubleHolder() {
        doubles = new ArrayList<>();
        for (int i = 0; i <= 24; i++) doubles.add(10.0);
    }

    /**
     * Updates hourly values within a specified time range.
     * 
     * This method sets all hours within the specified range (inclusive of start,
     * exclusive of end) to the given price value. It handles the special case
     * where the end hour is 24 by also setting that final slot to maintain
     * consistency in price schedules that end at midnight.
     * 
     * The method provides bounds checking to ensure the range doesn't exceed
     * the valid hour range (0-24).
     * 
     * @param fromValue Starting hour (inclusive, 0-23)
     * @param toValue Ending hour (exclusive, 1-24, automatically capped at 24)
     * @param price The price value to set for all hours in the range
     */
    public void update(Integer fromValue, Integer toValue, Double price) {
        // Ensure end value doesn't exceed valid hour range
        if (toValue > 24) toValue = 24;
        
        // Set price for all hours in the range [fromValue, toValue)
        for (int i = fromValue; i < toValue; i++) doubles.set(i, price);
        
        // Handle special case for rates ending exactly at midnight
        if (toValue == 24) doubles.set(toValue, price);
    }

    /**
     * Returns a string representation of all hourly values.
     * 
     * Creates a comma-separated string representation of all double values
     * enclosed in brackets, suitable for debugging, logging, and display
     * purposes. The format shows all 25 values (hours 0-24).
     * 
     * @return String representation in format "[10.0, 10.0, ...]"
     */
    @NonNull
    public String toString() {
        return "[" + doubles.stream().map(String::valueOf).collect(Collectors.joining(", ")) + "]";
    }
}
