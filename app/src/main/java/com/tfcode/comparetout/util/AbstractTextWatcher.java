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

package com.tfcode.comparetout.util;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * Abstract base class for text input validation and numeric conversion.
 * 
 * This utility class provides a foundation for implementing TextWatcher instances
 * that need to handle numeric input validation and conversion. It implements the
 * TextWatcher interface with empty default implementations for beforeTextChanged()
 * and onTextChanged(), allowing subclasses to focus only on the afterTextChanged()
 * method where the actual validation logic is typically implemented.
 * 
 * Key benefits:
 * - Reduces boilerplate code in TextWatcher implementations
 * - Provides robust numeric parsing with fallback to default values
 * - Handles common NumberFormatException scenarios gracefully
 * - Supports both integer and double precision numeric input
 * 
 * The class includes utility methods for safe numeric conversion that return
 * sensible default values (0 or 0.0) when parsing fails, preventing application
 * crashes from invalid user input while maintaining a smooth user experience.
 * 
 * Typical usage pattern:
 * - Extend this class and implement afterTextChanged()
 * - Use getDoubleOrZero() or getIntegerOrZero() for safe numeric conversion
 * - Apply validation logic and update UI or data models as needed
 */
public abstract class AbstractTextWatcher implements TextWatcher {
    /**
     * Default implementation - no action required before text changes.
     */
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    /**
     * Default implementation - no action required during text changes.
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /**
     * Safely convert text input to a double value with zero fallback.
     * 
     * Attempts to parse the editable text as a double value, returning 0.0
     * if the parsing fails due to invalid format. This prevents crashes from
     * user input validation while providing predictable behavior.
     * 
     * @param s the editable text content to parse
     * @return the parsed double value, or 0.0 if parsing fails
     */
    public Double getDoubleOrZero(Editable s) {
        double ret;
        try {
            ret = Double.parseDouble(s.toString());
        }catch (NumberFormatException nfe) {
            ret = 0D;
        }
        return ret;
    }

    /**
     * Safely convert text input to an integer value with zero fallback.
     * 
     * Attempts to parse the editable text as an integer value, returning 0
     * if the parsing fails due to invalid format. This prevents crashes from
     * user input validation while providing predictable behavior.
     * 
     * @param s the editable text content to parse
     * @return the parsed integer value, or 0 if parsing fails
     */
    public Integer getIntegerOrZero(Editable s) {
        int ret;
        try {
            ret = Integer.parseInt(s.toString());
        }catch (NumberFormatException nfe) {
            ret = 0;
        }
        return ret;
    }
}
