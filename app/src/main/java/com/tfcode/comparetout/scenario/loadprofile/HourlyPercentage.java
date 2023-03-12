package com.tfcode.comparetout.scenario.loadprofile;

public class HourlyPercentage {
    private int mBegin;
    private int mEnd;
    private double mPrice;

    public HourlyPercentage(int begin, int end, double d) {
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

    public double getPercentage() {
        return mPrice;
    }

    public void setPrice(double mPrice) {
        this.mPrice = mPrice;
    }
}
