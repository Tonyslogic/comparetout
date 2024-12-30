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

public class FiveMinuteEnergies {
    public Double pv;
    public Double load;
    public Double feed;
    public Double buy;
    public Double charge;

    public FiveMinuteEnergies(Double pv, Double load, Double feed, Double buy) {
        this.pv = (pv.isNaN()) ? 0d : pv;
        this.load = (load.isNaN()) ? 0d : load;
        this.feed = (feed.isNaN()) ? 0d: feed;
        this.buy = (buy.isNaN()) ? 0d: buy;
        this.charge = (pv + buy) - (load + feed);
    }

    public FiveMinuteEnergies(double pv, double load, double feed, double buy, double charge) {
        this.pv = pv;
        this.load = load;
        this.feed = feed;
        this.buy = buy;
        this.charge = charge;
    }
}
