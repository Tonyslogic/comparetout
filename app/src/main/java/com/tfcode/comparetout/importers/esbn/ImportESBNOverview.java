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

package com.tfcode.comparetout.importers.esbn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.icu.text.SimpleDateFormat;

import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.CredentialDialog;
import com.tfcode.comparetout.importers.ImportException;
import com.tfcode.comparetout.importers.ImportOverviewFragment;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;

public class ImportESBNOverview extends ImportOverviewFragment {

    private ESBNHDFClient mOpenAlphaESSClient;

    public static ImportESBNOverview newInstance() {
        return new ImportESBNOverview();
    }


    @Override
    protected void loadSystemListFromPreferences(TOUTCApplication application) {
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value4 = application.getDataStore()
               .data().firstOrError()
               .map(prefs -> prefs.get(systemList)).onErrorReturnItem("[]");
        String systemListJsonString =  value4.blockingGet();
        List<String> mprnListFromPreferences =
                new Gson().fromJson(systemListJsonString, new TypeToken<List<String>>(){}.getType());
        if (!(null == mprnListFromPreferences) && !(mprnListFromPreferences.isEmpty())) {
            mSerialNumbers = new ArrayList<>();
            mSerialNumbers.addAll(mprnListFromPreferences);
        }
    }

    public ImportESBNOverview() {
        // Required empty public constructor
        TAG = "ImportESBNOverview";
        mImporterType = ComparisonUIViewModel.Importer.ESBNHDF;
        APP_ID_KEY = "esbn_user_id";
        APP_SECRET_KEY = "esbn_password";
        GOOD_CREDENTIAL_KEY = "esbn_cred_good";
        SYSTEM_LIST_KEY = "esbn_system_list";
        SYSTEM_PREVIOUSLY_SELECTED = "esbn_system_previously_selected";
    }

    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context) && !("".equals(serialNumber))) {
            // start the catchup worker for the selected serial
//            Data inputData = new Data.Builder()
//                    .putString(ESBNCatchUpWorker.KEY_APP_ID, mAppID)
//                    .putString(ESBNCatchUpWorker.KEY_APP_SECRET, mAppSecret)
//                    .putString(ESBNCatchUpWorker.KEY_SYSTEM_SN, serialNumber)
//                    .putString(ESBNCatchUpWorker.KEY_START_DATE, format.format(startDate))
//                    .build();
//            OneTimeWorkRequest catchupWorkRequest =
//                    new OneTimeWorkRequest.Builder(ESBNCatchUpWorker.class)
//                            .setInputData(inputData)
//                            .addTag(serialNumber)
//                            .build();
//
//            WorkManager.getInstance(context).pruneWork();
//            WorkManager
//                    .getInstance(context)
//                    .beginUniqueWork(serialNumber, ExistingWorkPolicy.APPEND, catchupWorkRequest)
//                    .enqueue();
//
//            // start the daily worker for the selected serial
//            Data dailyData = new Data.Builder()
//                    .putString(ESBNDailyWorker.KEY_APP_ID, mAppID)
//                    .putString(ESBNDailyWorker.KEY_APP_SECRET, mAppSecret)
//                    .putString(ESBNDailyWorker.KEY_SYSTEM_SN, serialNumber)
//                    .build();
//            int delay = 25 - LocalDateTime.now().getHour(); // Going for 01:00 <-> 02:00
//            PeriodicWorkRequest dailyWorkRequest =
//                    new PeriodicWorkRequest.Builder(ESBNDailyWorker.class, 1, TimeUnit.DAYS)
//                            .setInputData(dailyData)
//                            .setInitialDelay(delay, TimeUnit.HOURS)
//                            .addTag(serialNumber + "daily")
//                            .build();
//            WorkManager
//                    .getInstance(context)
//                    .enqueueUniquePeriodicWork(serialNumber + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyWorkRequest);

            mFetchOngoing = true;
        }
    }

    @Override
    protected void reloadClient(String appId, String appSecret) throws ImportException {
        mOpenAlphaESSClient = new ESBNHDFClient(appId, appSecret);
        List<String> response = mOpenAlphaESSClient.fetchMPRNs();
        mSerialNumbers = new ArrayList<>();
        mSerialNumbers.addAll(response);
        String stringResponse = new Gson().toJson(response);
        if (!(null == getActivity()) && !(null == getActivity().getApplication()) ) {
            TOUTCApplication application = (TOUTCApplication) getActivity().getApplication();
            boolean x = application.putStringValueIntoDataStore(SYSTEM_LIST_KEY, stringResponse);
            if (!x) System.out.println("ImportESBNOverview::reLoadSystemList, failed to store list");
        }
    }

    @Override
    protected void setCredentialPrompt(CredentialDialog credentialDialog) {
        credentialDialog.setPrompts(R.string.userPrompt, R.string.passPrompt);
    }
}