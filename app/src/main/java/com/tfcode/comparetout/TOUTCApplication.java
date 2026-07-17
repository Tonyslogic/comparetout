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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import java.util.HashMap;
import java.util.Map;

import dagger.hilt.android.HiltAndroidApp;
import io.reactivex.rxjava3.core.Single;

/**
 * Main Application class for the TOUTC (Time of Use Tariff Comparison) application.
 * <p>
 * This class extends Android's Application class and serves as the global application context,
 * providing essential services for the entire application lifecycle. It manages secure data
 * storage using Android DataStore with RxJava for reactive operations, and implements
 * hardware-backed encryption using the Android Keystore system for sensitive data protection.
 * <p>
 * Key responsibilities:
 * - Initialize the application-wide DataStore for persistent settings
 * - Provide secure encryption/decryption services for sensitive data like API keys
 * - Manage application-level constants and shared resources
 * - Coordinate between the Android Keystore system and application data storage
 * <p>
 * The encryption functionality uses AES-256/GCM (see {@link CredentialCrypto}), with keys
 * stored in the Android hardware security module when available, providing protection
 * against key extraction even on rooted devices.
 */
@HiltAndroidApp
public class TOUTCApplication extends Application {

    private RxDataStore<Preferences> dataStore;
    static final String FIRST_USE = "first_use";

    /**
     * Initialize the application and set up core services.
     * <p>
     * Creates the RxDataStore instance for reactive preference management.
     * This DataStore provides thread-safe, asynchronous storage for application
     * settings and user preferences using Protocol Buffers serialization.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        dataStore = new RxPreferenceDataStoreBuilder(this, /*name=*/ "settings").build();
        // The one-time data migrations below only repair data written by OLDER app versions. On a fresh
        // install there is nothing to migrate, so gate them off: if the Room database file ("toutc_database")
        // doesn't exist yet (first ever launch) mark both migrations done instead of enqueuing them.
        // Otherwise they run, no-op, but the dashboard's "Finishing a one-time data update" card appears and
        // lingers as "pending" until the next restart. Existing installs enqueue exactly as before.
        boolean freshInstall = !getDatabasePath("toutc_database").exists();
        if (freshInstall) {
            // DataStore writes block; keep them off the main thread. No worker is enqueued on this path —
            // setting the DONE guards is enough for both the workers (which would no-op) and the dashboard
            // card (which hides once the guards read "true").
            new Thread(() -> {
                putStringValueIntoDataStore(
                        com.tfcode.comparetout.model.TimezoneRestampWorker.DONE_KEY, "true");
                putStringValueIntoDataStore(
                        com.tfcode.comparetout.model.PanelDataRefreshWorker.DONE_KEY, "true");
                // Nothing is scheduled on a fresh install — mark the credential
                // scrub (plans/source/security.md §1) done too.
                putStringValueIntoDataStore(
                        com.tfcode.comparetout.importers.WorkSpecCredentialScrub.DONE_KEY, "true");
            }).start();
        } else {
            // One-time: re-anchor already-imported data to the saved timezone (Phase 2,
            // plans/sim/timezone-and-rollout.md). Idempotent — unique work + an internal DataStore guard.
            com.tfcode.comparetout.model.TimezoneRestampWorker.enqueue(this);
            // One-time: discard pre-millis PV data (wrong grid) and refresh + re-simulate (Phase 3).
            // Idempotent — unique work + an internal DataStore guard.
            com.tfcode.comparetout.model.PanelDataRefreshWorker.enqueue(this);
            // One-time: re-enqueue daily fetch specs without embedded plaintext
            // credentials (plans/source/security.md §1). Idempotent — DataStore guard.
            new Thread(() ->
                    com.tfcode.comparetout.importers.WorkSpecCredentialScrub.runOnce(this)).start();
        }
    }

    /**
     * Encrypt a plaintext string with the app's Keystore-backed credential key.
     * <p>
     * Delegates to {@link CredentialCrypto}: new ciphertext is always the
     * authenticated v2 format (AES-256/GCM under the {@code toutcKey2} alias,
     * created on first use). See plans/source/security.md §3.
     *
     * @param clearText the plaintext string to encrypt
     * @return the {@code "v2:"}-prefixed Base64 blob
     * @throws Exception if encryption fails or key access is denied
     */
    public static String encryptString(String clearText) throws Exception {
        return CredentialCrypto.system().encrypt(clearText);
    }

    /**
     * Decrypt a blob produced by {@link #encryptString} — either the current v2
     * format or the legacy CBC format from older app versions (dispatch on the
     * {@code "v2:"} prefix; the legacy key is never deleted while legacy blobs
     * can exist).
     * <p>
     * Always throws on failure — including the restored-from-backup case where
     * the ciphertext survived but the device-bound key did not. Callers treat
     * any failure as "credentials absent, re-enter" (there is deliberately no
     * sentinel return value).
     *
     * @param encryptedText the stored blob
     * @return the decrypted plaintext string
     * @throws Exception on tamper, missing key, or corrupted data
     */
    public static String decryptString(String encryptedText) throws Exception {
        return CredentialCrypto.system().decrypt(encryptedText);
    }

    /**
     * Error fallback Preferences instance for DataStore operations.
     * <p>
     * This instance is returned when DataStore operations fail, providing
     * a safe fallback that prevents application crashes. All methods return
     * null or empty values, allowing the application to continue functioning
     * with default behaviors when preference storage is unavailable.
     */
    final Preferences pref_error = new Preferences() {
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
     * <p>
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
     * Read a string value previously stored with putStringValueIntoDataStore().
     * <p>
     * Blocks on the asynchronous DataStore using RxJava, returning an empty
     * string when the key is absent or the read fails.
     *
     * @param key the preference key to read
     * @return the stored string value, or "" if not present
     */
    public String getStringValueFromDataStore(String key) {
        Preferences.Key<String> PREF_KEY = PreferencesKeys.stringKey(key);
        return dataStore.data().firstOrError()
                .map(prefs -> {
                    String value = prefs.get(PREF_KEY);
                    return value == null ? "" : value;
                })
                .onErrorReturnItem("")
                .blockingGet();
    }

    /**
     * Get the application's DataStore instance for reactive preference access.
     * 
     * @return the RxDataStore instance for reading and writing application preferences
     */
    public RxDataStore<Preferences> getDataStore() {return dataStore;}
}
