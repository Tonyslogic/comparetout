/*
 * Copyright (c) 2023. Tony Finnerty
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

import android.app.Application;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import io.reactivex.rxjava3.core.Single;

/**
 * Main Application class for the TOUTC (Time of Use Tariff Comparison) application.
 * 
 * This class extends Android's Application class and serves as the global application context,
 * providing essential services for the entire application lifecycle. It manages secure data
 * storage using Android DataStore with RxJava for reactive operations, and implements
 * hardware-backed encryption using the Android Keystore system for sensitive data protection.
 * 
 * Key responsibilities:
 * - Initialize the application-wide DataStore for persistent settings
 * - Provide secure encryption/decryption services for sensitive data like API keys
 * - Manage application-level constants and shared resources
 * - Coordinate between the Android Keystore system and application data storage
 * 
 * The encryption functionality uses AES-256 with CBC mode and PKCS7 padding, with keys
 * stored in the Android hardware security module when available, providing protection
 * against key extraction even on rooted devices.
 */
public class TOUTCApplication extends Application {

    private RxDataStore<Preferences> dataStore;
    static final String FIRST_USE = "first_use";

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "toutcKey";

    /**
     * Initialize the application and set up core services.
     * 
     * Creates the RxDataStore instance for reactive preference management.
     * This DataStore provides thread-safe, asynchronous storage for application
     * settings and user preferences using Protocol Buffers serialization.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        dataStore = new RxPreferenceDataStoreBuilder(this, /*name=*/ "settings").build();
    }

    /**
     * Generate a new AES encryption key in the Android Keystore.
     * 
     * Creates a hardware-backed AES key with 256-bit strength using CBC mode
     * and PKCS7 padding. The key is stored in the Android Keystore system,
     * which provides hardware-level security on supported devices by storing
     * keys in a Trusted Execution Environment (TEE) or secure element.
     * 
     * @throws Exception if key generation fails due to hardware limitations
     *                   or security policy restrictions
     */
    public static void generateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);

            keyGenerator.init(builder.build());
            keyGenerator.generateKey();
        }
    }

    /**
     * Delete the encryption key from the Android Keystore.
     * 
     * Permanently removes the application's encryption key, effectively
     * making any previously encrypted data unrecoverable. This is typically
     * used during application reset or when changing security contexts.
     * 
     * @throws Exception if key deletion fails or keystore is inaccessible
     */
    public static void deleteKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        keyStore.deleteEntry(KEY_ALIAS);
    }

    /**
     * Encrypt a plaintext string using the application's keystore-backed key.
     * 
     * Uses AES encryption in CBC mode with PKCS7 padding to encrypt sensitive
     * data such as API keys or tokens. The initialization vector (IV) is
     * automatically generated and prepended to the encrypted data to ensure
     * that identical plaintexts produce different ciphertexts.
     * 
     * The method automatically generates a new key if one doesn't exist,
     * ensuring transparent key management for the application.
     * 
     * @param clearText the plaintext string to encrypt
     * @return Base64-encoded string containing IV + encrypted data
     * @throws Exception if encryption fails or key access is denied
     */
    public static String encryptString(String clearText) throws Exception {
        Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (null == secretKey){
            // Auto-generate key if it doesn't exist for seamless user experience
            generateKey();
            secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] iv = cipher.getIV();
        byte[] encryptedBytes = cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8));

        // Combine IV and encrypted data for storage - IV is needed for decryption
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        return Base64.encodeToString(combined, Base64.DEFAULT);
    }

    /**
     * Decrypt a previously encrypted string using the application's keystore key.
     * 
     * Decrypts Base64-encoded data that was encrypted with encryptString().
     * The method extracts the initialization vector from the beginning of
     * the encrypted data and uses it to properly decrypt the remainder.
     * 
     * @param encryptedText Base64-encoded string containing IV + encrypted data
     * @return the decrypted plaintext string, or "NoKey" if the key is unavailable
     * @throws Exception if decryption fails due to corrupted data or wrong key
     */
    public static String decryptString(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);

        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (null == secretKey) return "NoKey";

        byte[] combined = Base64.decode(encryptedText, Base64.DEFAULT);

        // Extract IV from the beginning of the combined data
        byte[] iv = new byte[cipher.getBlockSize()];
        System.arraycopy(combined, 0, iv, 0, iv.length);
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);

        // Decrypt the remaining data after the IV
        byte[] decryptedBytes = cipher.doFinal(combined, iv.length, combined.length - iv.length);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Error fallback Preferences instance for DataStore operations.
     * 
     * This instance is returned when DataStore operations fail, providing
     * a safe fallback that prevents application crashes. All methods return
     * null or empty values, allowing the application to continue functioning
     * with default behaviors when preference storage is unavailable.
     */
    Preferences pref_error = new Preferences() {
        @Nullable
        @Override
        public <T> T get(@NonNull Key<T> key) {
            return null;
        }

        @Override
        public <T> boolean contains(@NonNull Key<T> key) {
            return false;
        }

        @NonNull
        @Override
        public Map<Key<?>, Object> asMap() {
            return new HashMap<>();
        }
    };

    /**
     * Store a string value in the DataStore with error handling.
     * 
     * Provides a synchronous interface to the asynchronous DataStore system
     * for simple string value storage. Uses RxJava's blocking operations
     * to wait for the update completion while providing error recovery
     * through the fallback preferences instance.
     * 
     * @param key the preference key to store the value under
     * @param value the string value to store
     * @return true if the value was successfully stored, false on error
     */
    public boolean putStringValueIntoDataStore(String key, String value){
        boolean returnValue;
        Preferences.Key<String> PREF_KEY = PreferencesKeys.stringKey(key);
        Single<Preferences> updateResult =  dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREF_KEY, value);
            return Single.just(mutablePreferences);
        }).onErrorReturnItem(pref_error);

        returnValue = updateResult.blockingGet() != pref_error;

        return returnValue;
    }

    /**
     * Get the application's DataStore instance for reactive preference access.
     * 
     * @return the RxDataStore instance for reading and writing application preferences
     */
    public RxDataStore<Preferences> getDataStore() {return dataStore;}
}
