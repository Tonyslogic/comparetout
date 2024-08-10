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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.rxjava3.RxDataStore;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.CredentialDialog;
import com.tfcode.comparetout.importers.ImportException;
import com.tfcode.comparetout.importers.ImportOverviewFragment;
import com.tfcode.comparetout.util.SettingsViewModel;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

public class ImportESBNOverview extends ImportOverviewFragment {

    public static final String ESBN_SYSTEM_LIST_KEY = "esbn_system_list";
    public static final String ESBN_PREVIOUS_SELECTED_KEY = "esbn_system_previously_selected";

    public static ImportESBNOverview newInstance() {
        return new ImportESBNOverview();
    }


    @Override
    protected void loadSystemListFromPreferences(TOUTCApplication application) {
        mSerialNumbers = new ArrayList<>();
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value4 = application.getDataStore()
               .data().firstOrError()
               .map(prefs -> prefs.get(systemList)).onErrorReturnItem("[]");
        String systemListJsonString =  value4.blockingGet();
        List<String> mprnListFromPreferences =
                new Gson().fromJson(systemListJsonString, new TypeToken<List<String>>(){}.getType());
        if (!(null == mprnListFromPreferences) && !(mprnListFromPreferences.isEmpty())) {
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
        SYSTEM_LIST_KEY = ESBN_SYSTEM_LIST_KEY;
        SYSTEM_PREVIOUSLY_SELECTED = ESBN_PREVIOUS_SELECTED_KEY;
        mSelectSystemText = R.string.SelectMPRN;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Activity activity = getActivity();
        if (!(null == activity)) {
            TOUTCApplication application = ((TOUTCApplication) activity.getApplication());
            RxDataStore<Preferences> dataStore = application.getDataStore();
            SettingsViewModel settingsViewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
                @NonNull
                @Override
                public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                    if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
                        return (T) new SettingsViewModel(dataStore);
                    }
                    throw new IllegalArgumentException("Unknown ViewModel class");
                }
            }).get(SettingsViewModel.class);

            settingsViewModel.getPreferencesLiveData().observe(getViewLifecycleOwner(), preferences -> {
                Preferences.Key<String> KEY = PreferencesKeys.stringKey(ESBN_PREVIOUS_SELECTED_KEY);
                String newValue = preferences.get(KEY);
                if (!(null == newValue)) {
                    System.out.println("Observed a change in the settings for the selected systems ==> " + newValue);
                    mSerialNumber = newValue;
                    mMainHandler.post(() -> {
                        serialUpdated(getContext());
                        updateView();
                    });
                }
                Preferences.Key<String> KEY2 = PreferencesKeys.stringKey(ESBN_SYSTEM_LIST_KEY);
                String newValue2 = preferences.get(KEY2);
                if (!(null == newValue2)) {
                    System.out.println("Observed a change in the settings for the list of systems");
                    loadSystemListFromPreferences(((TOUTCApplication) activity.getApplication()));
                }
            });
            
        }
    }

    @Override
    protected void getCredentialsWithWarning(TextView credentialStatus, TOUTCApplication application) {
        // Must use the context of the subclass and not the super!
        Context context = this.getContext();
        if (null == context) return;
        MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(context);
        alert.setTitle("Unstable API");
        alert.setMessage("This function uses an unofficial ESBN API. It may break at any time. " +
                "If it does, you may still download the file manually and import using the menu " +
                "above. See help for how to do this.");
        alert.setPositiveButton(android.R.string.yes, (dialog, which) -> {
            credentialStatus.setText(R.string.loading);
            getCredentials(application);
        });
        alert.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.cancel());
        alert.show();
    }

    @Override
    protected void fetchOnClickDelegate(MaterialButton addFetchButton, TextView addFetchStatus, Context context) {
        // Must use the context of the subclass and not the super!
        Context subClassContext = this.getContext();
        if (null == subClassContext) return;
        MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(subClassContext);
        alert.setTitle("Unstable API");
        alert.setMessage("This function uses an unofficial ESBN API. It is currently broken. " +
                "You may still download the file manually. See help for how to do this. " +
                "Clicking ok will cancel any recurring fetch workers.");
        alert.setPositiveButton(android.R.string.yes, (dialog, which) ->
                cancelOngoingFetch(addFetchButton));
        alert.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.cancel());
        alert.show();
    }

    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
//        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
//        Context context = getContext();
//        if (!(null == context) && !("".equals(serialNumber))) {
            // start the catchup worker for the selected serial
//            Intent intent = new Intent(this.getContext(), ESBNFetchService.class);
//            intent.putExtra(ESBNFetchService.KEY_APP_ID, mAppID);
//            intent.putExtra(ESBNFetchService.KEY_APP_SECRET, mAppSecret);
//            intent.putExtra(ESBNFetchService.KEY_SYSTEM_SN, serialNumber);
//            intent.putExtra(ESBNFetchService.KEY_START_DATE, format.format(startDate));
//            Activity activity = this.getActivity();
//            if (!(null == activity)) {
//                activity.startForegroundService(intent);
//            }

            // start the daily worker for the selected serial
//            Data dailyData = new Data.Builder()
//                    .putString(ESBNCatchUpWorker.KEY_APP_ID, mAppID)
//                    .putString(ESBNCatchUpWorker.KEY_APP_SECRET, mAppSecret)
//                    .putString(ESBNCatchUpWorker.KEY_SYSTEM_SN, serialNumber)
//                    .build();
//            int delay = 25 - LocalDateTime.now().getHour(); // Going for 01:00 <-> 02:00
//            PeriodicWorkRequest dailyWorkRequest =
//                    new PeriodicWorkRequest.Builder(ESBNCatchUpWorker.class, 1, TimeUnit.DAYS)
//                            .setInputData(dailyData)
//                            .setInitialDelay(delay, TimeUnit.HOURS)
//                            .addTag(serialNumber + "daily")
//                            .build();
//            WorkManager
//                    .getInstance(context)
//                    .enqueueUniquePeriodicWork(serialNumber + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyWorkRequest);
//
//            mFetchOngoing = true;
//        }
    }

    @Override
    protected void reloadClient(String appId, String appSecret) throws ImportException {
        ESBNHDFClient mOpenAlphaESSClient = new ESBNHDFClient(appId, appSecret);
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
        credentialDialog.setPrompts(R.string.userPrompt, R.string.passPrompt,
                (!(null == mAppID) && !(mAppID.isEmpty())) ? mAppID : null);
    }

    @Override
    protected TableRow getSystemSelectionRow(Activity activity, boolean mCredentialsAreGood) {
        // For ESBN importer, we do not want to disable the manual input of MPRN,
        // so HDF loading will still work
        return super.getSystemSelectionRow(activity, true);
    }
}