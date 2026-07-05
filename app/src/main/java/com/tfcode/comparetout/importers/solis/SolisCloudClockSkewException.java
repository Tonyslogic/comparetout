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

package com.tfcode.comparetout.importers.solis;

/**
 * Fatal: the server answered HTTP 408, which SolisCloud uses to signal that
 * the signed Date header is more than ±15 minutes from server time. Retrying
 * cannot help — the fix is the device clock, so the worker surfaces a
 * distinct "check the phone's clock" message instead of looping.
 */
public class SolisCloudClockSkewException extends SolisCloudException {
    public SolisCloudClockSkewException(String s) {
        super(s);
    }
}
