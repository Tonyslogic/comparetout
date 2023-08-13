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
import androidx.datastore.preferences.rxjava2.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava2.RxDataStore;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Single;

public class TOUTCApplication extends Application {

    private RxDataStore<Preferences> dataStore;
    static final String FIRST_USE = "first_use";

    @Override
    public void onCreate() {
        super.onCreate();
        dataStore = new RxPreferenceDataStoreBuilder(this, /*name=*/ "settings").build();
    }

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

    boolean putStringValueIntoDataStore(String key, String value){
        System.out.println("Storing: " + key + ", " + value);
        boolean returnValue;
        Preferences.Key<String> PREF_KEY = PreferencesKeys.stringKey(key);
        Single<Preferences> updateResult =  dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(PREF_KEY, value);
            return Single.just(mutablePreferences);
        }).onErrorReturnItem(pref_error);

        returnValue = updateResult.blockingGet() != pref_error;

        System.out.println("Stored: " + key + ", " + value);
        return returnValue;
    }

    RxDataStore<Preferences> getDataStore() {return dataStore;}
}
