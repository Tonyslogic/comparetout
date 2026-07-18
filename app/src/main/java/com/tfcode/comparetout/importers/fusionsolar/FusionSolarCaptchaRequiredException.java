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

import androidx.annotation.Nullable;

/**
 * Fatal for unattended work: the portal demanded a captcha during login
 * ({@code verifyCodeCreate} on the validateUser response). Carries the
 * captcha image bytes when they could be fetched so the credential sheet can
 * display them and re-submit with the typed code. The worker maps this to a
 * "sign in again" notification — never a silent stall, never automated
 * solving.
 */
public class FusionSolarCaptchaRequiredException extends FusionSolarException {

    @Nullable
    private final byte[] mCaptchaImage;

    public FusionSolarCaptchaRequiredException(String s, @Nullable byte[] captchaImage) {
        super(s);
        mCaptchaImage = captchaImage;
    }

    /** JPEG bytes from {@code /unisso/verifycode}, or null when the fetch failed. */
    @Nullable
    public byte[] getCaptchaImage() {
        return mCaptchaImage;
    }
}
