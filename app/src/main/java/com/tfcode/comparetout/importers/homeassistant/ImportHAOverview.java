/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.homeassistant;

import static java.lang.Thread.sleep;

import android.annotation.SuppressLint;
import android.content.Context;
import android.icu.text.SimpleDateFormat;

import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.CredentialDialog;
import com.tfcode.comparetout.importers.ImportException;
import com.tfcode.comparetout.importers.ImportOverviewFragment;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

public class ImportHAOverview extends ImportOverviewFragment {

    public static ImportHAOverview newInstance() {
        return new ImportHAOverview();
    }


    @Override
    protected void loadSystemListFromPreferences(TOUTCApplication application) {
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value4 = application.getDataStore()
               .data().firstOrError()
               .map(prefs -> prefs.get(systemList)).onErrorReturnItem("[HomeAssistant]");
        String systemListJsonString =  value4.blockingGet();
        List<String> mprnListFromPreferences =
                new Gson().fromJson(systemListJsonString, new TypeToken<List<String>>(){}.getType());
        if (!(null == mprnListFromPreferences) && !(mprnListFromPreferences.isEmpty())) {
            mSerialNumbers = new ArrayList<>();
            mSerialNumbers.addAll(mprnListFromPreferences);
        }
    }

    public ImportHAOverview() {
        // Required empty public constructor
        TAG = "ImportESBNOverview";
        mImporterType = ComparisonUIViewModel.Importer.HOME_ASSISTANT;
        APP_ID_KEY = "ha_host";
        APP_SECRET_KEY = "ha_token";
        GOOD_CREDENTIAL_KEY = "ha_cred_good";
        SYSTEM_LIST_KEY = "ha_system_list";
        SYSTEM_PREVIOUSLY_SELECTED = "ha_system_previously_selected";
    }

    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context) && !("".equals(serialNumber))) {
            // start the catchup worker for the selected serial
            Data inputData = new Data.Builder()
                    .putString(HACatchupWorker.KEY_APP_ID, mAppID)
                    .putString(HACatchupWorker.KEY_APP_SECRET, mAppSecret)
                    .putString(HACatchupWorker.KEY_START_DATE, format.format(startDate))
                    .build();
            OneTimeWorkRequest catchupWorkRequest =
                    new OneTimeWorkRequest.Builder(HACatchupWorker.class)
                            .setInputData(inputData)
                            .addTag(serialNumber)
                            .build();

            WorkManager.getInstance(context).pruneWork();
            WorkManager
                    .getInstance(context)
                    .beginUniqueWork(serialNumber, ExistingWorkPolicy.APPEND, catchupWorkRequest)
                    .enqueue();

            // start the daily worker for the selected serial
            Data dailyData = new Data.Builder()
                    .putString(HACatchupWorker.KEY_APP_ID, mAppID)
                    .putString(HACatchupWorker.KEY_APP_SECRET, mAppSecret)
                    .build();
            int delay = 25 - LocalDateTime.now().getHour(); // Going for 01:00 <-> 02:00
            PeriodicWorkRequest dailyWorkRequest =
                    new PeriodicWorkRequest.Builder(HACatchupWorker.class, 1, TimeUnit.DAYS)
                            .setInputData(dailyData)
                            .setInitialDelay(delay, TimeUnit.HOURS)
                            .addTag(serialNumber + "daily")
                            .build();
            WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(serialNumber + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyWorkRequest);

            mFetchOngoing = true;
        }
    }

    @Override
    protected void reloadClient(String host, String token) throws ImportException {
        HADispatcher mHAClient = new HADispatcher(host, token);
        mHAClient.registerHandler("auth_ok", new MessageHandler<HAMessage>() {
            @Override
            public void handleMessage(HAMessage message) {
                mHAClient.setAuthorized(true);
                mAppID = host;
                mAppSecret = token;
                mFetchOngoing = false;
                mHAClient.stop();
            }

            @Override
            public Class<? extends HAMessage> getMessageClass() {
                return AuthOK.class;
            }
        });
        mHAClient.registerHandler("auth_invalid", new MessageHandler<HAMessage>() {
            @Override
            public void handleMessage(HAMessage message) {
                mHAClient.setAuthorized(false);
                mFetchOngoing = false;
                mHAClient.stop();
            }

            @Override
            public Class<? extends HAMessage> getMessageClass() {
                return AuthInvalid.class;
            }
        });
        mHAClient.start();
    }

    @Override
    protected void setCredentialPrompt(CredentialDialog credentialDialog) {
        credentialDialog.setPrompts(R.string.hostPrompt, R.string.tokenPrompt);
    }
}