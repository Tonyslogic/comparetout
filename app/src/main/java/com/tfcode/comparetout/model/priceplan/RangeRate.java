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

import java.util.Objects;

public class RangeRate {
    private int mBegin;
    private int mEnd;
    private double mPrice;

    public RangeRate(int begin, int end, double d) {
        mBegin = begin;
        mEnd = end;
        mPrice = d;
    }

    public int getBegin() {
        return mBegin;
    }

    public void setBegin(int mBegin) {
        this.mBegin = mBegin;
    }

    public int getEnd() {
        return mEnd;
    }

    public void setEnd(int mEnd) {
        this.mEnd = mEnd;
    }

    public double getPrice() {
        return mPrice;
    }

    public void setPrice(double mPrice) {
        this.mPrice = mPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RangeRate rangeRate = (RangeRate) o;
        return mBegin == rangeRate.mBegin && mEnd == rangeRate.mEnd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBegin, mEnd);
    }
}
