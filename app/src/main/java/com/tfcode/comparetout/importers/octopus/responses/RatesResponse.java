/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.importers.octopus.responses;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GET /v1/products/{p}/electricity-tariffs/{code}/standard-unit-rates/ and
 * .../standing-charges/ (public, paginated). Unit rates are pence/kWh inc
 * VAT; standing charges are pence/day inc VAT.
 */
public class RatesResponse {
    @SerializedName("count") public int count;
    @SerializedName("next") public String next;
    @SerializedName("results") public List<Rate> results;

    public static class Rate {
        @SerializedName("value_exc_vat") public double valueExcVat;
        @SerializedName("value_inc_vat") public double valueIncVat;
        /** ISO-8601 UTC; time-of-use tariffs repeat a daily window pattern. */
        @SerializedName("valid_from") public String validFrom;
        /** null = open-ended. */
        @SerializedName("valid_to") public String validTo;
        @SerializedName("payment_method") public String paymentMethod;
    }
}
