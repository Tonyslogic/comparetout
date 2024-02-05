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

package com.tfcode.comparetout.importers.alphaess;

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
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.CredentialDialog;
import com.tfcode.comparetout.importers.ImportOverviewFragment;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

public class ImportAlphaOverview extends ImportOverviewFragment {

    private OpenAlphaESSClient mOpenAlphaESSClient;

    public static ImportAlphaOverview newInstance() {
        return new ImportAlphaOverview();
    }

    @Override
    protected void loadSystemListFromPreferences(TOUTCApplication application) {
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value4 = application.getDataStore()
               .data().firstOrError()
               .map(prefs -> prefs.get(systemList)).onErrorReturnItem("{\"code\": 200, \"msg\": \"Success\", \"expMsg\": null, \"data\": []}");
        String systemListJsonString =  value4.blockingGet();
        GetEssListResponse getEssListResponse = new Gson().fromJson(systemListJsonString, GetEssListResponse.class);
        if (!(null == getEssListResponse) && !(getEssListResponse.data.isEmpty())) {
            mSerialNumbers = new ArrayList<>();
            for (GetEssListResponse.DataItem system : getEssListResponse.data) {
                mSerialNumbers.add(system.sysSn);
            }
        }
    }

    public ImportAlphaOverview() {
        // Required empty public constructor
        TAG = "ImportAlphaOverview";
        mImporterType = ComparisonUIViewModel.Importer.ALPHAESS;
        APP_ID_KEY = "app_id";
        APP_SECRET_KEY = "app_secret";
        GOOD_CREDENTIAL_KEY = "cred_good";
        SYSTEM_LIST_KEY = "system_list";
        SYSTEM_PREVIOUSLY_SELECTED = "system_previously_selected";
    }

    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context) && !("".equals(serialNumber))) {
            // start the catchup worker for the selected serial
            Data inputData = new Data.Builder()
                    .putString(CatchUpWorker.KEY_APP_ID, mAppID)
                    .putString(CatchUpWorker.KEY_APP_SECRET, mAppSecret)
                    .putString(CatchUpWorker.KEY_SYSTEM_SN, serialNumber)
                    .putString(CatchUpWorker.KEY_START_DATE, format.format(startDate))
                    .build();
            OneTimeWorkRequest catchupWorkRequest =
                    new OneTimeWorkRequest.Builder(CatchUpWorker.class)
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
                    .putString(DailyWorker.KEY_APP_ID, mAppID)
                    .putString(DailyWorker.KEY_APP_SECRET, mAppSecret)
                    .putString(DailyWorker.KEY_SYSTEM_SN, serialNumber)
                    .build();
            int delay = 25 - LocalDateTime.now().getHour(); // Going for 01:00 <-> 02:00
            PeriodicWorkRequest dailyWorkRequest =
                    new PeriodicWorkRequest.Builder(DailyWorker.class, 1, TimeUnit.DAYS)
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
    protected void setCredentialPrompt(CredentialDialog credentialDialog) {
        credentialDialog.setPrompts(R.string.appId, R.string.appSecret);
    }

    @Override
    protected void reloadClient(String appId, String appSecret) throws AlphaESSException {
        mOpenAlphaESSClient = new OpenAlphaESSClient(appId, appSecret);
        GetEssListResponse response = mOpenAlphaESSClient.getEssList();
        if (null == response) throw new AlphaESSException("err.code=7001 err.msg=The network was not available" );
        List<GetEssListResponse.DataItem> systems = response.data;
        mSerialNumbers = new ArrayList<>();
        for (GetEssListResponse.DataItem system : systems) {
            mSerialNumbers.add(system.sysSn);
        }
        String stringResponse = new Gson().toJson(response);
        if (!(null == getActivity()) && !(null == getActivity().getApplication()) ) {
            TOUTCApplication application = (TOUTCApplication) getActivity().getApplication();
            boolean x = application.putStringValueIntoDataStore(SYSTEM_LIST_KEY, stringResponse);
            if (!x) System.out.println("ImportAlphaOverview::reLoadSystemList, failed to store list");
        }
    }
}