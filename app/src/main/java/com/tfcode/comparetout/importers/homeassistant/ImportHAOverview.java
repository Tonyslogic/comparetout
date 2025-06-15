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

import io.reactivex.rxjava3.core.Single;

/**
 * Import overview fragment for Home Assistant integration.
 * 
 * This class manages the connection to Home Assistant and handles energy sensor data retrieval.
 * It extends ImportOverviewFragment to provide a consistent interface for importing energy data
 * from Home Assistant installations. The class facilitates user authentication, sensor discovery,
 * and background data synchronization through worker tasks.
 * 
 * Key responsibilities:
 * - Authenticate with Home Assistant WebSocket API
 * - Discover and configure energy sensors (solar, battery, grid)  
 * - Schedule periodic data import tasks
 * - Manage credential storage and validation
 * 
 * @see ImportOverviewFragment for base functionality
 * @see HADispatcher for WebSocket communication
 * @see HACatchupWorker for background data synchronization
 */
public class ImportHAOverview extends ImportOverviewFragment {

    private static final Logger LOGGER = Logger.getLogger(ImportHAOverview.class.getName());
    private static final String HA_SENSORS_KEY = "ha_sensors";
    static final String HA_COBAT_KEY = "ha_cobat";
    private EnergySensors mEnergySensors;
    private ImportHAOverview mImportHAOverview;

    /**
     * Factory method to create new instance of ImportHAOverview fragment.
     * 
     * This method follows the Android Fragment pattern of using static factory methods
     * rather than constructors to ensure proper initialization. The self-reference is
     * stored to enable callback operations from inner classes.
     * 
     * @return A new configured instance of ImportHAOverview
     */
    public static ImportHAOverview newInstance() {
        ImportHAOverview fragment = new ImportHAOverview();
        fragment.mImportHAOverview = fragment;
        return fragment;
    }

    /**
     * Removes stored preferences and resets battery capacity for a given serial number.
     * 
     * This method is called during cleanup operations when a system is being removed
     * or reset. It specifically resets the estimated battery capacity to prevent
     * stale data from affecting new configurations.
     * 
     * @param mSerialNumber The serial number of the system to clean up (unused in HA implementation)
     */
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

    /**
     * Loads system list and energy sensor configuration from persistent storage.
     * 
     * This method initializes the fragment state by retrieving previously stored
     * Home Assistant system configuration and sensor definitions. It handles both
     * the system identification (defaulting to "HomeAssistant") and the detailed
     * sensor mappings required for energy data collection.
     * 
     * The method performs two main operations:
     * 1. Load system list - establishes the connection to Home Assistant
     * 2. Load sensor configuration - retrieves previously discovered energy sensors
     * 
     * @param application The application context for accessing data store
     */
    @Override
    protected void loadSystemListFromPreferences(TOUTCApplication application) {
        // Load the system list configuration, defaulting to HomeAssistant if none exists
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(SYSTEM_LIST_KEY);
        Single<String> value4 = application.getDataStore()
               .data().firstOrError()
               .map(prefs -> prefs.get(systemList)).onErrorReturnItem("[HomeAssistant]");
        String systemListJsonString =  value4.blockingGet();
        List<String> mprnListFromPreferences =
                new Gson().fromJson(systemListJsonString, new TypeToken<List<String>>(){}.getType());
        
        // Initialize system list and set selection state if credentials are valid
        if (!(null == mprnListFromPreferences) && !(mprnListFromPreferences.isEmpty())) {
            mSerialNumbers = new ArrayList<>();
            mSerialNumbers.addAll(mprnListFromPreferences);
            if (mCredentialsAreGood) {
                mSerialNumber = "HomeAssistant";
                mSystemSelected = true;
            }
        }

        // Load previously discovered energy sensor configuration
        Preferences.Key<String> sensorsKey = PreferencesKeys.stringKey(HA_SENSORS_KEY);
        Single<String> value5 = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(sensorsKey)).onErrorReturnItem("{}");
        String sensorsJsonString =  value5.blockingGet();
        EnergySensors energySensors = new EnergySensors();
        
        // Attempt to deserialize stored sensor configuration with error handling
        try {
            energySensors =
                    new Gson().fromJson(sensorsJsonString, new TypeToken<EnergySensors>(){}.getType());
        } catch (Exception e) {
            System.out.println("ImportHAOverview::loadSystemListFromPreferences, " +
                    "failed to load sensors from preferences");
        }
        if (!(null == energySensors)) {mEnergySensors = energySensors;}
    }

    /**
     * Constructor for ImportHAOverview fragment.
     * 
     * Initializes the fragment with Home Assistant-specific configuration values.
     * These constants define the keys used for storing credentials, system information,
     * and other persistent data in the application's data store. The constructor
     * establishes the importer type to ensure proper handling by the parent class.
     */
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

    /**
     * Configures the credential dialog prompts for Home Assistant connection.
     * 
     * This method customizes the credential input dialog to show appropriate
     * prompts for Home Assistant connection (host URL and access token).
     * It provides a default host format to guide users in entering the correct
     * WebSocket endpoint URL.
     * 
     * @param credentialDialog The dialog to configure with Home Assistant-specific prompts
     */
    @Override
    protected void setCredentialPrompt(CredentialDialog credentialDialog) {
        credentialDialog.setPrompts(R.string.hostPrompt, R.string.tokenPrompt,
                (!(null == mAppID) && !(mAppID.isEmpty()))
                        ? mAppID
                        : "http://<HOST>:8123/api/websocket");
    }

    /**
     * Creates the system selection row for the Home Assistant interface.
     * 
     * This method builds the UI row that allows users to view and select
     * Home Assistant energy sensors. The button is only enabled when credentials
     * are valid and systems are available. The status text shows the current
     * system state for user feedback.
     * 
     * @param activity The parent activity context
     * @param mCredentialsAreGood Whether valid credentials have been established
     * @return TableRow containing the system selection button and status display
     */
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

    /**
     * Displays the discovered energy sensors in a dialog for user review.
     * 
     * This method formats the energy sensor configuration as pretty-printed JSON
     * and presents it to the user in a dialog. Upon confirmation, it sets the
     * system as selected and triggers UI updates. This allows users to review
     * what sensors were discovered before proceeding with data import.
     */
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

    /**
     * Initiates background workers for Home Assistant data collection.
     * 
     * This method sets up two types of workers:
     * 1. Catchup worker - performs initial historical data import from the specified start date
     * 2. Daily worker - schedules recurring daily imports to keep data current
     * 
     * The workers are configured with the necessary credentials and sensor information
     * to autonomously collect energy data from Home Assistant. The daily worker is
     * scheduled to run during off-peak hours (1-2 AM) to minimize impact.
     * 
     * @param serialNumber The system identifier (used for worker tagging)
     * @param startDate The date from which to begin historical data import
     */
    @Override
    protected void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context) && !("".equals(serialNumber))) {
            // Start the catchup worker for historical data import from the selected start date
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

            // Start the daily recurring worker for ongoing data synchronization
            Data dailyData = new Data.Builder()
                    .putString(HACatchupWorker.KEY_HOST, mAppID)
                    .putString(HACatchupWorker.KEY_TOKEN, mAppSecret)
                    .putString(HACatchupWorker.KEY_SENSORS, new Gson().toJson(mEnergySensors))
                    .build();
            
            // Schedule for off-peak hours (1-2 AM) to minimize system impact
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

    /**
     * Establishes connection to Home Assistant WebSocket API for credential validation.
     * 
     * This method creates a new HADispatcher instance to handle WebSocket communication
     * with Home Assistant. It registers handlers for authentication responses and
     * initiates the connection process. The method is called during credential
     * validation to verify that the provided host and token are valid.
     * 
     * @param host The Home Assistant WebSocket URL
     * @param token The long-lived access token for authentication
     * @throws ImportException if connection to Home Assistant fails
     */
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
    /**
     * Handles the energy preferences result from Home Assistant.
     * 
     * This inner class processes the response from Home Assistant's energy preferences API
     * to discover and configure energy sensors. It parses the energy sources (solar, battery, grid)
     * and creates appropriate sensor mappings for data collection. The class is responsible for
     * transforming Home Assistant's energy configuration into the application's internal
     * sensor representation.
     * 
     * Key responsibilities:
     * - Parse energy source configuration from Home Assistant
     * - Create sensor mappings for solar generation, battery, and grid interactions
     * - Store sensor configuration for use by data collection workers
     * - Update UI state upon successful sensor discovery
     */
    class EnergyPrefsResultHandler implements MessageHandler<EnergyPrefsResult> {

        private final Logger LOGGER = Logger.getLogger(ImportHAOverview.class.getName());

        private List<String> statSolarEnergyFrom;
        private List<BatterySensor> statBatteries;
        private List<String> statGridEnergyFrom;
        private List<String> statGridEnergyTo;
        private final HADispatcher mHAClient;
        private final Activity mActivity;

        /**
         * Constructs energy sensor configuration from discovered sensor IDs.
         * 
         * This method creates an EnergySensors object that contains all the
         * sensor identifiers discovered during the energy preferences query.
         * This configuration is used by background workers to know which
         * sensors to monitor for energy data collection.
         * 
         * @return EnergySensors object containing all discovered sensor mappings
         */
        public EnergySensors getSensors() {
            EnergySensors ret = new EnergySensors();
            ret.solarGeneration = statSolarEnergyFrom;
            ret.batteries = statBatteries;
            ret.gridExports = statGridEnergyTo;
            ret.gridImports = statGridEnergyFrom;
            return ret;
        }

        /**
         * Constructor for EnergyPrefsResultHandler.
         * 
         * @param mHAClient The HADispatcher instance for WebSocket communication
         * @param activity The parent activity for UI updates
         */
        public EnergyPrefsResultHandler(HADispatcher mHAClient, Activity activity) {
            this.mHAClient = mHAClient;
            this.mActivity = activity;
        }

        /**
         * Processes the energy preferences result message from Home Assistant.
         * 
         * This method parses the energy configuration response and categorizes each
         * energy source by type (solar, battery, grid). It creates appropriate sensor
         * mappings for data collection and stores the configuration for future use.
         * Upon successful processing, it updates the UI to reflect the discovered sensors.
         * 
         * The method handles three types of energy sources:
         * - Solar: Maps energy generation sensors
         * - Battery: Maps both charging and discharging sensors  
         * - Grid: Maps both import and export flow sensors
         * 
         * @param message The energy preferences result message from Home Assistant
         */
        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("EnergyPrefsResultHandler.handleMessage");
            EnergyPrefsResult result = (EnergyPrefsResult) message;
            if (result.isSuccess()) {
                // Initialize sensor lists for discovered energy sources
                statSolarEnergyFrom = new ArrayList<>();
                statBatteries = new ArrayList<>();
                List<EnergySource> energySources = result.getResult().getEnergySources();
                
                // Process each energy source and categorize by type
                for (EnergySource energySource : energySources) {
                    switch (energySource.getType()) {
                        case "solar":
                            // Solar panels only generate energy (energy_from)
                            statSolarEnergyFrom.add(energySource.getStatEnergyFrom());
                            break;
                        case "battery":
                            // Batteries have both charging (energy_to) and discharging (energy_from) sensors
                            BatterySensor batterySensor = new BatterySensor();
                            batterySensor.batteryCharging = energySource.getStatEnergyTo();
                            batterySensor.batteryDischarging = energySource.getStatEnergyFrom();
                            statBatteries.add(batterySensor);
                            break;
                        case "grid":
                            // Grid has bidirectional flows - imports (from) and exports (to)
                            statGridEnergyFrom = energySource.getFlowFrom().stream().map(Flow::getStatEnergyFrom).collect(Collectors.toList());
                            statGridEnergyTo = energySource.getFlowTo().stream().map(Flow::getStatEnergyTo).collect(Collectors.toList());
                            break;
                    }

                }

                // Store the discovered sensor configuration and update UI
                mEnergySensors = getSensors();
                String stringResponse = new Gson().toJson(mEnergySensors);
                if (!(null == mActivity) && !(null == mActivity.getApplication()) ) {
                    TOUTCApplication application = (TOUTCApplication) mActivity.getApplication();
                    boolean x = application.putStringValueIntoDataStore(HA_SENSORS_KEY, stringResponse);
                    if (!x) System.out.println("ImportHAOverview::reloadClient, failed to store sensors");
                }
                
                // Update system selection state and refresh UI
                mSerialNumber = "HomeAssistant";
                mSystemSelected = true;
                mMainHandler.post(() -> {serialUpdated(mActivity);});
                mMainHandler.post(mImportHAOverview::updateView);
            }
            mHAClient.stop();
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {
            return EnergyPrefsResult.class;
        }
    }

    /**
     * Handles successful authentication with Home Assistant.
     * 
     * This inner class processes the "auth_ok" message received after successful
     * authentication with Home Assistant's WebSocket API. Upon receiving this message,
     * it stores the validated credentials and initiates the energy preferences query
     * to discover available energy sensors. This handler represents the successful
     * path in the authentication flow.
     */
    private class AuthOKHandler implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;
        private final String host;
        private final String token;

        /**
         * Constructor for AuthOKHandler.
         * 
         * @param mHAClient The HADispatcher instance managing the WebSocket connection  
         * @param host The Home Assistant WebSocket URL that was successfully authenticated
         * @param token The access token that was validated
         */
        public AuthOKHandler(HADispatcher mHAClient, String host, String token) {
            this.mHAClient = mHAClient;
            this.host = host;
            this.token = token;
        }

        /**
         * Processes successful authentication and initiates energy sensor discovery.
         * 
         * This method is called when Home Assistant confirms successful authentication.
         * It updates the client's authorization state, stores the validated credentials,
         * and sends an energy preferences request to discover available energy sensors.
         * The energy preferences query is essential for identifying which sensors
         * to monitor for solar, battery, and grid energy data.
         * 
         * @param message The authentication success message from Home Assistant
         */
        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthOKHandler.handleMessage");
            // Mark the client as authorized and store validated credentials
            mHAClient.setAuthorized(true);
            mAppID = host;
            mAppSecret = token;
            mFetchOngoing = false;
            
            // Initiate energy sensor discovery process
            EnergyPrefsResultHandler energyPrefsResultHandler =
                    new EnergyPrefsResultHandler(mHAClient, getActivity());
            EnergyPrefsRequest request = new EnergyPrefsRequest();
            request.setId(mHAClient.generateId());
            mHAClient.sendMessage(request, energyPrefsResultHandler);
        }

        @Override
        public Class<? extends HAMessage> getMessageClass() {return AuthOK.class;}
    }

    /**
     * Handles authentication failure with Home Assistant.
     * 
     * This inner class processes the "auth_invalid" message received when 
     * authentication with Home Assistant's WebSocket API fails. This typically
     * occurs when the provided access token is invalid, expired, or lacks
     * the necessary permissions. The handler ensures proper cleanup of the
     * connection and updates the application state to reflect the failed authentication.
     */
    private class AuthNotOKHandler implements MessageHandler<HAMessage> {
        private final HADispatcher mHAClient;

        /**
         * Constructor for AuthNotOKHandler.
         * 
         * @param mHAClient The HADispatcher instance that needs to be cleaned up on auth failure
         */
        public AuthNotOKHandler(HADispatcher mHAClient) {
            this.mHAClient = mHAClient;
        }

        /**
         * Processes authentication failure and performs cleanup.
         * 
         * This method is called when Home Assistant rejects the authentication attempt.
         * It marks the client as unauthorized, stops any ongoing fetch operations,
         * and properly closes the WebSocket connection. This ensures the application
         * doesn't attempt to use invalid credentials and provides clear feedback
         * to the user about the authentication status.
         * 
         * @param message The authentication failure message from Home Assistant
         */
        @Override
        public void handleMessage(HAMessage message) {
            LOGGER.info("AuthInvalidHandler.handleMessage");
            // Mark client as unauthorized and stop operations
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