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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Format/dispatch tests for {@link CredentialCrypto} (plans/source/security.md §3),
 * run on the JVM with plain {@code javax.crypto} AES keys injected through the
 * {@link CredentialCrypto.KeyProvider} seam — the AndroidKeyStore itself is not
 * exercisable here, only the ciphertext formats and their dispatch are.
 */
public class CredentialCryptoTest {

    private SecretKey v2Key;
    private SecretKey legacyKey;
    private CredentialCrypto crypto;

    private static SecretKey newAesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    private CredentialCrypto.KeyProvider provider(SecretKey v2, SecretKey legacy) {
        return new CredentialCrypto.KeyProvider() {
            @Override
            public SecretKey v2Key() {
                return v2;
            }

            @Override
            public SecretKey legacyKey() {
                return legacy;
            }
        };
    }

    @Before
    public void setUp() throws Exception {
        v2Key = newAesKey();
        legacyKey = newAesKey();
        crypto = new CredentialCrypto(provider(v2Key, legacyKey));
    }

    // ── v2 (GCM) ────────────────────────────────────────────────────────

    @Test
    public void v2RoundTrip() throws Exception {
        String blob = crypto.encrypt("app-secret-π-☀");
        assertTrue(CredentialCrypto.isV2(blob));
        assertEquals("app-secret-π-☀", crypto.decrypt(blob));
    }

    @Test
    public void freshIvPerEncryption() throws Exception {
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"));
    }

    @Test
    public void tamperedV2Throws() throws Exception {
        String blob = crypto.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(blob.substring(CredentialCrypto.V2_PREFIX.length()));
        raw[raw.length - 1] ^= 0x01; // flip a tag bit
        String tampered = CredentialCrypto.V2_PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThrows(Exception.class, () -> crypto.decrypt(tampered));
    }

    // ── legacy (CBC) dispatch ───────────────────────────────────────────

    /** Build a legacy-format blob exactly as the old code did: base64(iv16 + CBC
     * ciphertext), wrapped at 76 chars like android.util.Base64.DEFAULT. */
    private String legacyBlob(String clearText, boolean wrapped) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new IvParameterSpec(iv));
        byte[] ct = cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ct, 0, combined, iv.length, ct.length);
        return wrapped
                ? Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(combined) + "\n"
                : Base64.getEncoder().encodeToString(combined);
    }

    @Test
    public void legacyUnwrappedBlobDecrypts() throws Exception {
        String blob = legacyBlob("legacy-secret", false);
        assertFalse(CredentialCrypto.isV2(blob));
        assertEquals("legacy-secret", crypto.decrypt(blob));
    }

    @Test
    public void legacyWrappedBlobDecrypts() throws Exception {
        // android.util.Base64.DEFAULT line-wraps long output — the dispatch
        // must tolerate embedded newlines (MIME decoding).
        String blob = legacyBlob("a-long-enough-secret-to-wrap-the-base64-line-"
                + "0123456789012345678901234567890123456789", true);
        assertTrue(blob.contains("\n"));
        assertEquals("a-long-enough-secret-to-wrap-the-base64-line-"
                + "0123456789012345678901234567890123456789", crypto.decrypt(blob));
    }

    @Test
    public void missingLegacyKeyThrows() throws Exception {
        String blob = legacyBlob("secret", false);
        CredentialCrypto noLegacy = new CredentialCrypto(provider(v2Key, null));
        // Restored-backup case: ciphertext without its device-bound key must
        // throw (mapped by callers to "credentials absent"), never a sentinel.
        assertThrows(Exception.class, () -> noLegacy.decrypt(blob));
    }

    @Test
    public void emptyBlobThrows() {
        assertThrows(Exception.class, () -> crypto.decrypt(""));
        assertThrows(Exception.class, () -> crypto.decrypt(null));
    }
}
