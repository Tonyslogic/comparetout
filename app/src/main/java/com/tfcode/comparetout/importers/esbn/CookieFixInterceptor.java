/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.esbn;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kotlin.Pair;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Response;

public class CookieFixInterceptor implements Interceptor {

    List<String> responseCookies = new ArrayList<>();

    void clearCookies() {
        responseCookies = new ArrayList<>();
    }

    List<String> getResponseCookies() {return responseCookies;}

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {

        Response response = chain.proceed(chain.request());

        Headers headers = response.headers();
        for (Pair<? extends String, ? extends String> header : headers) {
            if (header.getFirst().equals("set-cookie")) {
                responseCookies.add(header.getSecond());
            }
        }

        return response;
    }
}