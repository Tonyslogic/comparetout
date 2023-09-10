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

@Entity(tableName = "alphaESSRawPower", primaryKeys = {"sysSn", "uploadTime"})
public class AlphaESSRawPower {
    @NonNull
    private String sysSn = "";
    @NonNull
    private String uploadTime = ""; // 2023-08-26 23:57:12"
    private double ppv;
    private double load;
    private double cbat;
    private double feedIn;
    private double gridCharge;
    private double pchargingPile;

    @NonNull
    public String getSysSn() {
        return sysSn;
    }

    public void setSysSn(@NonNull String sysSn) {
        this.sysSn = sysSn;
    }

    @NonNull
    public String getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(@NonNull String uploadTime) {
        this.uploadTime = uploadTime;
    }

    public double getPpv() {
        return ppv;
    }

    public void setPpv(double ppv) {
        this.ppv = ppv;
    }

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    public double getCbat() {
        return cbat;
    }

    public void setCbat(double cbat) {
        this.cbat = cbat;
    }

    public double getFeedIn() {
        return feedIn;
    }

    public void setFeedIn(double feedIn) {
        this.feedIn = feedIn;
    }

    public double getGridCharge() {
        return gridCharge;
    }

    public void setGridCharge(double gridCharge) {
        this.gridCharge = gridCharge;
    }

    public double getPchargingPile() {
        return pchargingPile;
    }

    public void setPchargingPile(double pchargingPile) {
        this.pchargingPile = pchargingPile;
    }
}
