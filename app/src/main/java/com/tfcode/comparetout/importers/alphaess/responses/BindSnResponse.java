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

package com.tfcode.comparetout.importers.alphaess.responses;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;

/**
 * Envelope for the bind-SN flow POSTs — getVerificationCode and bindSn
 * (plans/source/alpha.md §1). Unlike the GET responses, non-200 codes are
 * meaningful to the caller (6003 already-bound is a success outcome; 6004
 * check-code and 6053 rate-limit are actionable), so [code] is surfaced
 * rather than mapped to an exception. [data] is kept as a raw JsonElement
 * because its shape is undocumented: for getVerificationCode the
 * verification code arrives "either in the response body or by email"
 * (CharlesGillanders/homeassistant-alphaESS troubleshooting), for bindSn it
 * is a don't-care success marker.
 */
public class BindSnResponse {
    @SerializedName("code")
    public int code;
    @SerializedName("msg")
    public String msg;
    @SerializedName("data")
    public JsonElement data;

    /**
     * The verification code when it came back in-band — the whole point of
     * driving the API instead of the portal (the email leg is the broken
     * part). Accepts a bare string/number payload or an object wrapping it
     * under "code"/"verificationCode"; booleans and anything else are the
     * bindSn-style success markers, not codes, and yield null.
     */
    @Nullable
    public String inBandCode() {
        String direct = asCodeString(data);
        if (direct != null) return direct;
        if (data != null && data.isJsonObject()) {
            String wrapped = asCodeString(data.getAsJsonObject().get("code"));
            if (wrapped != null) return wrapped;
            return asCodeString(data.getAsJsonObject().get("verificationCode"));
        }
        return null;
    }

    @Nullable
    private static String asCodeString(@Nullable JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return null;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return null;
        String s = p.getAsString().trim();
        return s.isEmpty() ? null : s;
    }
}
