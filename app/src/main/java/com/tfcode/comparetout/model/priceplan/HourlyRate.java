package com.tfcode.comparetout.model.priceplan;

public class HourlyRate {
    private int mBegin;
    private int mEnd;
    private double mPrice;

    public HourlyRate(int begin, int end, double d) {
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
}
