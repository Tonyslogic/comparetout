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

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.Query;

public interface OpenAlphaESSService {

    @GET("api/getOneDateEnergyBySn")
    Call<ResponseBody> getOneDateEnergyBySn(
            @HeaderMap Map<String, String> headers,
            @Query("sysSn") String sysSn,
            @Query("queryDate") String queryDate
    );

    @GET("api/getOneDayPowerBySn")
    Call<ResponseBody> getOneDayPowerBySn(
            @HeaderMap Map<String, String> headers,
            @Query("sysSn") String sysSn,
            @Query("queryDate") String queryDate
    );

    @GET("api/getEssList")
    Call<ResponseBody> getESSList(
            @HeaderMap Map<String, String> headers
    );
}
