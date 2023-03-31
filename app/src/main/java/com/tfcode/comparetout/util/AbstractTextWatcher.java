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
