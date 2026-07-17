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

package com.tfcode.comparetout;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Credential encryption (plans/source/security.md §3).
 * <p>
 * Two on-disk formats coexist:
 * <ul>
 *   <li><b>v2</b> (all new writes): {@code "v2:" + base64(iv12 + ciphertext+tag)},
 *       AES-256/GCM (authenticated) under the {@code toutcKey2} Keystore alias.</li>
 *   <li><b>legacy</b> (no prefix): {@code base64(iv16 + ciphertext)},
 *       AES-256/CBC/PKCS7 under the original {@code toutcKey} alias. Decrypt-only;
 *       the alias is never deleted while legacy blobs can still exist. Legacy blobs
 *       were Base64-wrapped by android.util.Base64.DEFAULT, so decoding uses the
 *       MIME decoder (tolerates line breaks).</li>
 * </ul>
 * Key access sits behind {@link KeyProvider} so the format/dispatch logic is
 * JVM-unit-testable with plain {@code javax.crypto} keys; production uses
 * {@link KeystoreKeyProvider} (keys generated in, and never leaving, the
 * AndroidKeyStore — see the key-generation code there).
 */
public final class CredentialCrypto {

    public static final String V2_PREFIX = "v2:";
    static final String LEGACY_KEY_ALIAS = "toutcKey";
    static final String V2_KEY_ALIAS = "toutcKey2";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    // Android providers register "PKCS7Padding"; the JVM's SunJCE only knows
    // "PKCS5Padding" (identical for AES's 16-byte blocks). Try both so the
    // legacy path also runs under plain-JVM unit tests.
    private static final String[] CBC_TRANSFORMATIONS =
            {"AES/CBC/PKCS7Padding", "AES/CBC/PKCS5Padding"};
    private static final int GCM_TAG_BITS = 128;
    private static final int CBC_IV_BYTES = 16;

    /** Supplies the AES keys; injectable for JVM tests. */
    interface KeyProvider {
        /** The v2 (GCM) key, created on first use. */
        SecretKey v2Key() throws Exception;
        /** The legacy (CBC) key, or null when it was never created — never auto-created. */
        SecretKey legacyKey() throws Exception;
    }

    private final KeyProvider mKeys;

    CredentialCrypto(KeyProvider keys) {
        mKeys = keys;
    }

    private static CredentialCrypto sSystem;

    /** The production instance, backed by the AndroidKeyStore. */
    static synchronized CredentialCrypto system() {
        if (sSystem == null) sSystem = new CredentialCrypto(new KeystoreKeyProvider());
        return sSystem;
    }

    /** True when [blob] is in the v2 (GCM, authenticated) format. */
    public static boolean isV2(String blob) {
        return blob != null && blob.startsWith(V2_PREFIX);
    }

    /** Encrypt — always writes the v2 format. */
    String encrypt(String clearText) throws Exception {
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, mKeys.v2Key());
        byte[] iv = cipher.getIV();
        // The v2 format hard-codes a 12-byte IV on the decrypt side; every
        // mainstream provider generates exactly that, but fail loudly if not.
        if (iv.length != 12) throw new IllegalStateException("unexpected GCM IV length " + iv.length);
        byte[] ct = cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ct, 0, combined, iv.length, ct.length);
        return V2_PREFIX + Base64.getEncoder().encodeToString(combined);
    }

    /** Decrypt either format. Throws on tamper, missing key, or bad input. */
    String decrypt(String blob) throws Exception {
        if (blob == null || blob.isEmpty()) throw new IllegalArgumentException("empty blob");
        return isV2(blob) ? decryptV2(blob) : decryptLegacy(blob);
    }

    private String decryptV2(String blob) throws Exception {
        byte[] combined = Base64.getDecoder().decode(blob.substring(V2_PREFIX.length()));
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        // GCM IV length is whatever the cipher produced at encrypt time (12 on
        // every mainstream provider); the tag rides at the end of the ciphertext.
        int ivLen = 12;
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, combined, 0, ivLen);
        cipher.init(Cipher.DECRYPT_MODE, mKeys.v2Key(), spec);
        byte[] clear = cipher.doFinal(combined, ivLen, combined.length - ivLen);
        return new String(clear, StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String blob) throws Exception {
        SecretKey key = mKeys.legacyKey();
        if (key == null) {
            // Restored-from-backup / wiped-Keystore case: ciphertext without its
            // device-bound key. Failing here (instead of returning a sentinel)
            // lands every caller in the "credentials absent — re-enter" path.
            throw new IllegalStateException("legacy credential key is not available");
        }
        byte[] combined = Base64.getMimeDecoder().decode(blob);
        Cipher cipher = cbcCipher();
        byte[] iv = new byte[CBC_IV_BYTES];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        byte[] clear = cipher.doFinal(combined, iv.length, combined.length - iv.length);
        return new String(clear, StandardCharsets.UTF_8);
    }

    private static Cipher cbcCipher() throws Exception {
        Exception last = null;
        for (String transformation : CBC_TRANSFORMATIONS) {
            try {
                return Cipher.getInstance(transformation);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last;
    }

    /** Production keys: generated in, and never extractable from, the AndroidKeyStore. */
    private static final class KeystoreKeyProvider implements KeyProvider {

        @Override
        public SecretKey v2Key() throws Exception {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(V2_KEY_ALIAS, null);
            if (key != null) return key;
            javax.crypto.KeyGenerator generator = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                    V2_KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                            | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                            android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        }

        @Override
        public SecretKey legacyKey() throws Exception {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            return (SecretKey) keyStore.getKey(LEGACY_KEY_ALIAS, null);
        }
    }
}
