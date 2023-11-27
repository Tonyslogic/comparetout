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

package com.tfcode.comparetout.importers.alphaess.responses;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GetEssListResponse {
    @SerializedName("code")
    public int code;
    @SerializedName("msg")
    public String msg;
    @SerializedName("expMsg")
    public String expMsg;
    @SerializedName("data")
    public List<DataItem> data;

    // Getter and setter methods for all fields

    public static class DataItem {
        @SerializedName("sysSn")
        public String sysSn;
        @SerializedName("popv")
        public double popv;
        @SerializedName("minv")
        public String minv;
        @SerializedName("poinv")
        public double poinv;
        @SerializedName("cobat")
        public double cobat;
        @SerializedName("mbat")
        public String mbat;
        @SerializedName("surplusCobat")
        public double surplusCobat;
        @SerializedName("usCapacity")
        public double usCapacity;
        @SerializedName("emsStatus")
        public String emsStatus;
    }
}




