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

package com.tfcode.comparetout.importers.homeassistant.messages;

import com.google.gson.annotations.SerializedName;

public abstract class HAMessageWithID extends HAMessage{
    @SerializedName("id")
    private int id;

    // Result frames carry success plus (on failure) an error payload. Declared once here so
    // every *Result subclass surfaces HA-side errors to its handler instead of silently logging.
    // Subclasses must NOT re-declare a "success" field (Gson rejects duplicate field names).
    @SerializedName("success")
    private Boolean success;
    @SerializedName("error")
    private HAError error;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isSuccess() {
        return !(null == success) && success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public HAError getError() {
        return error;
    }

    /** Human-readable error summary for logs/notifications; safe when no error payload exists. */
    public String getErrorDescription() {
        if (null == error) return "unspecified error";
        return error.getCode() + ": " + error.getMessage();
    }
}
