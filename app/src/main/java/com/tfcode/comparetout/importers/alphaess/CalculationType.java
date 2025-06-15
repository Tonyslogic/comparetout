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

package com.tfcode.comparetout.importers.alphaess;

/**
 * Enumeration defining calculation methods for data aggregation.
 * 
 * This enum specifies the available mathematical operations for aggregating
 * time-series energy data during import and processing operations. Different
 * calculation types are appropriate for different types of energy measurements
 * and analysis requirements.
 * 
 * The enum provides a cycling mechanism to allow users to toggle between
 * calculation types in user interface components.
 */
public enum CalculationType {
    /** Sum aggregation - adds all values in the time period */
    SUM,
    /** Average aggregation - calculates mean value over the time period */
    AVG;

    /**
     * Returns the next calculation type in the enumeration cycle.
     * 
     * This method enables cycling through calculation types in user interfaces,
     * allowing users to toggle between SUM and AVG calculations. When called
     * on the last enum value, it wraps around to the first value.
     * 
     * @return The next CalculationType in the cycle
     */
    public CalculationType next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
