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

public abstract class AbstractTextWatcher implements TextWatcher {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public Double getDoubleOrZero(Editable s) {
        double ret;
        try {
            ret = Double.parseDouble(s.toString());
        }catch (NumberFormatException nfe) {
            ret = 0D;
        }
        return ret;
    }

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
