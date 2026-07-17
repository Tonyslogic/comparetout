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

package com.tfcode.comparetout.importers.octopus;

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
import com.tfcode.comparetout.importers.ImportOverviewFragment;
import com.tfcode.comparetout.importers.octopus.responses.AccountResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * Octopus Energy overview tab: credentials (account number + API key),
 * MPAN auto-discovery from /accounts/, fetch/daily-sync control, delete.
 *
 * Mirrors ImportAlphaOverview (auto-discovered system list) rather than ESBN
 * (manual MPRN entry): a valid key + account yields the meter points without
 * the user ever typing an MPAN.
 */
public class ImportOctopusOverview extends ImportOverviewFragment {

    public static final String OCTOPUS_ACCOUNT_KEY = "octopus_account";
    public static final String OCTOPUS_API_KEY_KEY = "octopus_api_key";
    public static final String OCTOPUS_GOOD_CREDENTIAL_KEY = "octopus_cred_good";
    public static final String OCTOPUS_SYSTEM_LIST_KEY = "octopus_system_list";
    public static final String OCTOPUS_PREVIOUS_SELECTED_KEY = "octopus_system_previously_selected";

    public static ImportOctopusOverview newInstance() {
        return new ImportOctopusOverview();
    }

    public ImportOctopusOverview() {
        // Required empty public constructor
        TAG = "ImportOctopusOverview";
        mImporterType = ComparisonUIViewModel.Importer.OCTOPUS;
        // The account number is the "id" and the API key the "secret" (OQ-5).
        APP_ID_KEY = OCTOPUS_ACCOUNT_KEY;
        APP_SECRET_KEY = OCTOPUS_API_KEY_KEY;
        GOOD_CREDENTIAL_KEY = OCTOPUS_GOOD_CREDENTIAL_KEY;
        SYSTEM_LIST_KEY = OCTOPUS_SYSTEM_LIST_KEY;
        SYSTEM_PREVIOUSLY_SELECTED = OCTOPUS_PREVIOUS_SELECTED_KEY;
        mSelectSystemText = R.string.SelectSystem;
    }

    @Override
    protected void loadSystemListFromPreferences(TOUTCApplication application) {
        mSerialNumbers = new ArrayList<>();
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(systemList)).onErrorReturnItem("[]");
        String systemListJsonString = value.blockingGet();
        List<OctopusSystem> systems = new Gson().fromJson(systemListJsonString,
                new TypeToken<List<OctopusSystem>>(){}.getType());
        if (!(null == systems) && !systems.isEmpty()) {
            for (OctopusSystem system : systems) mSerialNumbers.add(system.getSysSn());
        }
    }

    @Override
    protected void reloadClient(String account, String apiKey) throws OctopusException {
        OctopusRestClient client = new OctopusRestClient(apiKey);
        AccountResponse accountResponse = client.getAccount(account);
        List<OctopusSystem> systems = OctopusSystem.fromAccount(accountResponse);
        if (systems.isEmpty())
            throw new OctopusException("No electricity meter points found on account " + account);
        mSerialNumbers = new ArrayList<>();
        for (OctopusSystem system : systems) mSerialNumbers.add(system.getSysSn());
        String stringResponse = new Gson().toJson(systems);
        if (!(null == getActivity()) && !(null == getActivity().getApplication())) {
            TOUTCApplication application = (TOUTCApplication) getActivity().getApplication();
            boolean x = application.putStringValueIntoDataStore(SYSTEM_LIST_KEY, stringResponse);
            if (!x) System.out.println("ImportOctopusOverview::reloadClient, failed to store list");
        }
    }

    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context) && !("".equals(serialNumber))) {
            // start the catchup worker for the selected system. No credentials in
            // the Data (plans/source/security.md §1) — the worker resolves them
            // from the encrypted DataStore via CredentialStore.
            Data inputData = new Data.Builder()
                    .putString(OctopusCatchUpWorker.KEY_SYSTEM_SN, serialNumber)
                    .putString(OctopusCatchUpWorker.KEY_START_DATE, format.format(startDate))
                    .build();
            OneTimeWorkRequest catchupWorkRequest =
                    new OneTimeWorkRequest.Builder(OctopusCatchUpWorker.class)
                            .setInputData(inputData)
                            .addTag(serialNumber)
                            .build();

            WorkManager.getInstance(context).pruneWork();
            WorkManager
                    .getInstance(context)
                    .beginUniqueWork(serialNumber, ExistingWorkPolicy.APPEND, catchupWorkRequest)
                    .enqueue();

            // Daily incremental sync: the catch-up worker resumes from the
            // latest stored date when no start date is supplied.
            Data dailyData = new Data.Builder()
                    .putString(OctopusCatchUpWorker.KEY_SYSTEM_SN, serialNumber)
                    .build();
            int delay = 25 - LocalDateTime.now().getHour(); // Going for 01:00 <-> 02:00
            PeriodicWorkRequest dailyWorkRequest =
                    new PeriodicWorkRequest.Builder(OctopusCatchUpWorker.class, 1, TimeUnit.DAYS)
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
        credentialDialog.setPrompts(R.string.octopusAccountPrompt, R.string.octopusApiKeyPrompt,
                (!(null == mAppID) && !(mAppID.isEmpty())) ? mAppID : null);
    }
}
