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

package com.tfcode.comparetout.importers;

import android.content.Context;

import androidx.annotation.Nullable;

import com.tfcode.comparetout.CredentialCrypto;
import com.tfcode.comparetout.TOUTCApplication;

/**
 * The single read path for data-source credentials (plans/source/security.md §1).
 * <p>
 * Secrets never travel in WorkManager input {@code Data} (WorkManager persists
 * inputs unencrypted in its own database): enqueue sites pass identifiers only,
 * and every credentialed worker resolves its secrets here at run time — the
 * encrypted blob is read from the shared settings DataStore (the same keys the
 * credential dialogs have always written) and decrypted in-process.
 * <p>
 * Reading through this accessor also opportunistically upgrades legacy-format
 * ciphertext to the authenticated v2 format (security.md §3): a blob that
 * decrypts but isn't {@code "v2:"}-prefixed is re-encrypted and written back.
 * <p>
 * Any failure — unset keys, or ciphertext whose device-bound Keystore key is
 * gone (restore to a new device) — returns null, which callers must surface as
 * "credentials absent, re-enter", never as a retry loop.
 */
public final class CredentialStore {

    /** A credential pair; the field meaning depends on the source. */
    public static final class Credentials {
        /** appId / host / account number / API key-id. */
        public final String first;
        /** appSecret / token / API key / secret. */
        public final String second;

        Credentials(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * The credentialed cloud sources and their DataStore keys — the identical
     * keys both the UI2 credential sheets and the legacy CredentialDialogs
     * write, so this accessor works for state saved by any app version.
     */
    public enum Source {
        ALPHAESS("app_id", "app_secret"),
        // The keys the legacy ESBN CredentialDialog wrote (ImportESBNOverview),
        // reused by the UI2 experimental cloud-sync strip.
        ESBN("esbn_user_id", "esbn_password"),
        FUSION_SOLAR("fusionsolar_username", "fusionsolar_password"),
        HOME_ASSISTANT("ha_host", "ha_token"),
        OCTOPUS("octopus_account", "octopus_api_key"),
        SOLIS("solis_key_id", "solis_secret");

        final String firstKey;
        final String secondKey;

        Source(String firstKey, String secondKey) {
            this.firstKey = firstKey;
            this.secondKey = secondKey;
        }
    }

    private CredentialStore() {
    }

    /**
     * Both halves of [source]'s credentials, decrypted — or null when either is
     * missing or undecryptable.
     */
    @Nullable
    public static Credentials get(Context context, Source source) {
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof TOUTCApplication)) return null;
        TOUTCApplication app = (TOUTCApplication) appContext;
        String first = decryptAndUpgrade(app, source.firstKey);
        String second = decryptAndUpgrade(app, source.secondKey);
        if (first == null || second == null) return null;
        return new Credentials(first, second);
    }

    @Nullable
    private static String decryptAndUpgrade(TOUTCApplication app, String key) {
        String blob = app.getStringValueFromDataStore(key);
        if (blob == null || blob.isEmpty() || "null".equals(blob)) return null;
        String clear;
        try {
            clear = TOUTCApplication.decryptString(blob);
        } catch (Exception e) {
            return null;
        }
        if (clear.isEmpty()) return null;
        if (!CredentialCrypto.isV2(blob)) {
            // Legacy CBC blob that still decrypts — converge it to the
            // authenticated format. Best effort: a failed write just means the
            // next read tries again.
            try {
                app.putStringValueIntoDataStore(key, TOUTCApplication.encryptString(clear));
            } catch (Exception ignored) {
            }
        }
        return clear;
    }
}
