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

package com.tfcode.comparetout.importers.esbn.responses;

import com.google.gson.annotations.SerializedName;

public class SettingsResponse {

    @SerializedName("remoteResource")
    public String remoteResource;

    @SerializedName("retryLimit")
    public int retryLimit;

    @SerializedName("trimSpacesInPassword")
    public boolean trimSpacesInPassword;

    @SerializedName("api")
    public String api;

    @SerializedName("csrf")
    public String csrf;

    @SerializedName("transId")
    public String transId;

    @SerializedName("pageViewId")
    public String pageViewId;

    @SerializedName("suppressElementCss")
    public boolean suppressElementCss;

    @SerializedName("isPageViewIdSentWithHeader")
    public boolean isPageViewIdSentWithHeader;

    @SerializedName("allowAutoFocusOnPasswordField")
    public boolean allowAutoFocusOnPasswordField;

    @SerializedName("pageMode")
    public int pageMode;

    @SerializedName("config")
    public Config config;

    @SerializedName("hosts")
    public Hosts hosts;

    @SerializedName("locale")
    public Locale locale;

    @SerializedName("xhrSettings")
    public XhrSettings xhrSettings;

    // Inner classes for nested JSON objects

    public static class Config {
        @SerializedName("showSignupLink")
        public String showSignupLink;

        @SerializedName("sendHintOnSignup")
        public String sendHintOnSignup;

        @SerializedName("includePasswordRequirements")
        public String includePasswordRequirements;

        @SerializedName("enableRememberMe")
        public String enableRememberMe;

        @SerializedName("operatingMode")
        public String operatingMode;

        @SerializedName("forgotPasswordLinkOverride")
        public String forgotPasswordLinkOverride;

        @SerializedName("announceVerCompleteMsg")
        public String announceVerCompleteMsg;
    }

    public static class Hosts {
        @SerializedName("tenant")
        public String tenant;

        @SerializedName("policy")
        public String policy;

        @SerializedName("static")
        public String staticUrl;
    }

    public static class Locale {
        @SerializedName("lang")
        public String lang;
    }

    public static class XhrSettings {
        @SerializedName("retryEnabled")
        public boolean retryEnabled;

        @SerializedName("retryMaxAttempts")
        public int retryMaxAttempts;

        @SerializedName("retryDelay")
        public int retryDelay;

        @SerializedName("retryExponent")
        public int retryExponent;

        @SerializedName("retryOn")
        public String[] retryOn;
    }
}
