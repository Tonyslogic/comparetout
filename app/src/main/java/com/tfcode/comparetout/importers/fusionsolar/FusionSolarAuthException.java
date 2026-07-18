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

package com.tfcode.comparetout.importers.fusionsolar;

/**
 * Fatal: the FusionSolar portal rejected the account credentials
 * (observed vocabulary: {@code errorCode 406} "Login failed. Enter the
 * correct username and password."), or the account is locked, or the login
 * response was not the documented contract at all. Retrying cannot help;
 * the worker fails with a "re-enter your credentials" notification.
 */
public class FusionSolarAuthException extends FusionSolarException {
    public FusionSolarAuthException(String s) {
        super(s);
    }
}
