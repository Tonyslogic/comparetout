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
 * GET /v1/products/ (public, paginated). One entry per Octopus product;
 * {@code direction} distinguishes IMPORT products from export (Outgoing) ones.
 */
public class ProductsResponse {
    @SerializedName("count") public int count;
    @SerializedName("next") public String next;
    @SerializedName("results") public List<Product> results;

    public static class Product {
        @SerializedName("code") public String code;
        @SerializedName("full_name") public String fullName;
        @SerializedName("display_name") public String displayName;
        @SerializedName("description") public String description;
        @SerializedName("is_variable") public boolean isVariable;
        @SerializedName("is_green") public boolean isGreen;
        @SerializedName("is_tracker") public boolean isTracker;
        @SerializedName("is_prepay") public boolean isPrepay;
        @SerializedName("is_business") public boolean isBusiness;
        @SerializedName("is_restricted") public boolean isRestricted;
        @SerializedName("brand") public String brand;
        /** "IMPORT" or "EXPORT". */
        @SerializedName("direction") public String direction;
        @SerializedName("available_from") public String availableFrom;
        /** null while the product is open for sign-up. */
        @SerializedName("available_to") public String availableTo;
        @SerializedName("term") public Integer term;
    }
}
