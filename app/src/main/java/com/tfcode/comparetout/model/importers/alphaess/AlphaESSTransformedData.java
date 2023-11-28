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

package com.tfcode.comparetout.model.importers.alphaess;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "alphaESSTransformedData", primaryKeys = {"sysSn", "date", "minute"})
public class AlphaESSTransformedData {
    @NonNull
    private String sysSn = "";
    @NonNull
    private String date = ""; // YYY-MM-DD
    @NonNull
    private String minute = ""; // HH:mm
    private double pv;
    private double load;
    private double feed;
    private double buy;

    @NonNull
    public String getSysSn() {
        return sysSn;
    }

    public void setSysSn(@NonNull String sysSn) {
        this.sysSn = sysSn;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    @NonNull
    public String getMinute() {
        return minute;
    }

    public void setMinute(@NonNull String minute) {
        this.minute = minute;
    }

    public double getPv() {
        return pv;
    }

    public void setPv(double pv) {
        this.pv = pv;
    }

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    public double getFeed() { return feed; }

    public void setFeed(double feed) { this.feed = feed;}

    public double getBuy() { return buy; }

    public void setBuy(double buy) { this.buy = buy; }
}
