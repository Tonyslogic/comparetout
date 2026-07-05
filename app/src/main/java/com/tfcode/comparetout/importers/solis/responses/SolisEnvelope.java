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

package com.tfcode.comparetout.importers.solis.responses;

import com.google.gson.JsonElement;

/**
 * The uniform SolisCloud response wrapper:
 * {@code {"success":true,"code":"0","msg":"success","data":...}}.
 *
 * {@code code} is a String ("0" = success; failures are codes like "R0000",
 * "B0011", "I0000") — unlike AlphaESS's integer codes. {@code data} is kept
 * as a raw element because its shape varies per endpoint (object, array, or
 * a paged {@code {page:{records:[...]}}} block); the client parses it into
 * the endpoint-specific class after checking {@code code}.
 */
public class SolisEnvelope {
    public Boolean success;
    public String code;
    public String msg;
    public JsonElement data;
}
