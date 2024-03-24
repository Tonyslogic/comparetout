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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.view.Gravity;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.CredentialDialog;
import com.tfcode.comparetout.importers.ImportException;
import com.tfcode.comparetout.importers.ImportOverviewFragment;
import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsRequest;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalid;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOK;
import com.tfcode.comparetout.importers.homeassistant.messages.energyPrefsResult.EnergyPrefsResult;
import com.tfcode.comparetout.importers.homeassistant.messages.energyPrefsResult.EnergySource;
import com.tfcode.comparetout.importers.homeassistant.messages.energyPrefsResult.Flow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.reactivex.Single;

public class ImportHAOverview extends ImportOverviewFragment {

    private static final Logger LOGGER = Logger.getLogger(ImportHAOverview.class.getName());
    private static final String HA_SENSORS_KEY = "ha_sensors";
    static final String HA_COBAT_KEY = "ha_cobat";
    private EnergySensors mEnergySensors;
    private ImportHAOverview mImportHAOverview;

    public static ImportHAOverview newInstance() {
        ImportHAOverview fragment = new ImportHAOverview();
        fragment.mImportHAOverview = fragment;
        return fragment;
    }

    @Override
    protected void removePreferencesForSN(String mSerialNumber) {
        Activity activity = getActivity();
        if (!(null == activity)) {
            TOUTCApplication application = (TOUTCApplication)activity.getApplication();
            boolean x = application.putStringValueIntoDataStore(HA_COBAT_KEY, "0.0");
            if (!x)
                System.out.println("ImportHAOverview::removePreferencesForSN, " +
                        "failed to store reset estimatedBatteryCapacity");
        }
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
            if (mCredentialsAreGood) {
                mSerialNumber = "HomeAssistant";
                mSystemSelected = true;
            }
        }

        Preferences.Key<String> sensorsKey = PreferencesKeys.stringKey(HA_SENSORS_KEY);
        Single<String> value5 = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(sensorsKey)).onErrorReturnItem("{}");
        String sensorsJsonString =  value5.blockingGet();
        EnergySensors energySensors = new EnergySensors();
        try {
            energySensors =
                    new Gson().fromJson(sensorsJsonString, new TypeToken<EnergySensors>(){}.getType());
        } catch (Exception e) {
            System.out.println("ImportHAOverview::loadSystemListFromPreferences, " +
                    "failed to load sensors from preferences");
        }
        if (!(null == energySensors)) {mEnergySensors = energySensors;}
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
    protected void setCredentialPrompt(CredentialDialog credentialDialog) {
        credentialDialog.setPrompts(R.string.hostPrompt, R.string.tokenPrompt,
                (!(null == mAppID) && !(mAppID.isEmpty()))
                        ? mAppID
                        : "http://<HOST>:8123/api/websocket");
    }

    @Override
    protected TableRow getSystemSelectionRow(Activity activity, boolean mCredentialsAreGood) {
        TableRow systemSelectionRow = new TableRow(activity);

        MaterialButton systemButton = new MaterialButton(activity);
        systemButton.setText(R.string.view_ha_sensors);
        systemButton.setEnabled(mCredentialsAreGood && !(null == mSerialNumbers));
        TextView systemStatus = new TextView(activity);
        systemStatus.setText(!(null == mSerialNumber) ? mSerialNumber : (null == mSerialNumbers) ? "None registered" : "Not set");
        systemStatus.setGravity(Gravity.CENTER);
        systemButton.setOnClickListener(v -> showEnergySensors());
        systemSelectionRow.addView(systemButton);
        systemSelectionRow.addView(systemStatus);
        return systemSelectionRow;
    }

    private void showEnergySensors() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String prettyJsonString = gson.toJson(mEnergySensors);
        Context context = getContext();
        if (!(null == context)) new AlertDialog.Builder(context)
                .setTitle("Energy Sensors")
                .setMessage(prettyJsonString)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mSerialNumber = "HomeAssistant";
                    mSystemSelected = true;
                    serialUpdated(context);
                    mMainHandler.post(this::updateView);
                })
                .show();
    }

    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context) && !("".equals(serialNumber))) {
            // start the catchup worker for the selected serial
            Data inputData = new Data.Builder()
                    .putString(HACatchupWorker.KEY_HOST, mAppID)
                    .putString(HACatchupWorker.KEY_TOKEN, mAppSecret)
                    .putString(HACatchupWorker.KEY_START_DATE, format.format(startDate))
                    .putString(HACatchupWorker.KEY_SENSORS, new Gson().toJson(mEnergySensors))
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
                    .putString(HACatchupWorker.KEY_HOST, mAppID)
                    .putString(HACatchupWorker.KEY_TOKEN, mAppSecret)
                    .putString(HACatchupWorker.KEY_SENSORS, new Gson().toJson(mEnergySensors))
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
        try {
            HADispatcher mHAClient = new HADispatcher(host, token);
            mHAClient.registerHandler("auth_ok", new AuthOKHandler(mHAClient, host, token));
            mHAClient.registerHandler("auth_invalid", new AuthNotOKHandler(mHAClient));
            mHAClient.start();
        } catch (Exception e) {
            throw new ImportException("Failed to connect to Home Assistant");
        }
    }
    class EnergyPrefsResultHandler implements MessageHandler<EnergyPrefsResult> {

        private final Logger LOGGER = Logger.getLogger(ImportHAOverview.class.getName());

        private List<String> statSolarEnergyFrom;
        private List<BatterySensor> statBatteries;
        private List<String> statGridEnergyFrom;
        private List<String> statGridEnergyTo;
        private final HADispatcher mHAClient;
        private final Activity mActivity;

        public EnergySensors getSensors() {
            EnergySensors ret = new EnergySensors();
            ret.solarGeneration = statSolarEnergyFrom;
            ret.batteries = statBatteries;
            ret.gridExports = statGridEnergyTo;
            ret.gridImports = statGridEnergyFrom;
            return ret;
        }

        public EnergyPrefsResultHandler(HADispatcher mHAClient, Activity activity) {
            this.mHAClient = mHAClient;
            this.mActivity = activity;
        }

        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("EnergyPrefsResultHandler.handleMessage");
            EnergyPrefsResult result = (EnergyPrefsResult) message;
            if (result.isSuccess()) {
                statSolarEnergyFrom = new ArrayList<>();
                statBatteries = new ArrayList<>();
                List<EnergySource> energySources = result.getResult().getEnergySources();
                for (EnergySource energySource : energySources) {
                    switch (energySource.getType()) {
                        case "solar":
                            statSolarEnergyFrom.add(energySource.getStatEnergyFrom());
                            break;
                        case "battery":
                            BatterySensor batterySensor = new BatterySensor();
                            batterySensor.batteryCharging = energySource.getStatEnergyTo();
                            batterySensor.batteryDischarging = energySource.getStatEnergyFrom();
                            statBatteries.add(batterySensor);
                            break;
                        case "grid":
                            statGridEnergyFrom = energySource.getFlowFrom().stream().map(Flow::getStatEnergyFrom).collect(Collectors.toList());
                            statGridEnergyTo = energySource.getFlowTo().stream().map(Flow::getStatEnergyTo).collect(Collectors.toList());
                            break;
                    }

                }

                mEnergySensors = getSensors();
                String stringResponse = new Gson().toJson(mEnergySensors);
                if (!(null == mActivity) && !(null == mActivity.getApplication()) ) {
                    TOUTCApplication application = (TOUTCApplication) mActivity.getApplication();
                    boolean x = application.putStringValueIntoDataStore(HA_SENSORS_KEY, stringResponse);
                    if (!x) System.out.println("ImportHAOverview::reloadClient, failed to store sensors");
                }
                mSerialNumber = "HomeAssistant";
                mSystemSelected = true;
                serialUpdated(mActivity);
                mMainHandler.post(mImportHAOverview::updateView);
            }
            mHAClient.stop();
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {
            return EnergyPrefsResult.class;
        }
    }

    private class AuthOKHandler implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;
        private final String host;
        private final String token;

        public AuthOKHandler(HADispatcher mHAClient, String host, String token) {
            this.mHAClient = mHAClient;
            this.host = host;
            this.token = token;
        }

        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthOKHandler.handleMessage");
            mHAClient.setAuthorized(true);
            mAppID = host;
            mAppSecret = token;
            mFetchOngoing = false;
            EnergyPrefsResultHandler energyPrefsResultHandler =
                    new EnergyPrefsResultHandler(mHAClient, getActivity());
            EnergyPrefsRequest request = new EnergyPrefsRequest();
            request.setId(mHAClient.generateId());
            mHAClient.sendMessage(request, energyPrefsResultHandler);
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {return AuthOK.class;}
    }

    private class AuthNotOKHandler implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;

        public AuthNotOKHandler(HADispatcher mHAClient) {
            this.mHAClient = mHAClient;
        }

        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthInvalidHandler.handleMessage");
            mHAClient.setAuthorized(false);
            mFetchOngoing = false;
            mHAClient.stop();
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {
            return AuthInvalid.class;
        }
    }
}