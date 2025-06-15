/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Abstract base class for generation data processing workers.
 * 
 * This class provides the foundation for background workers that generate synthetic
 * energy system data based on real-world measurements. It implements the Android
 * WorkManager Worker interface to perform long-running data generation tasks
 * in the background, with progress notifications and cancellation support.
 * 
 * The worker generates several types of energy system components:
 * - Load profiles from historical consumption data
 * - Inverter configurations based on system specifications  
 * - Solar panel generation data with realistic power curves
 * - Battery system data including charge/discharge patterns
 * - Battery scheduling information for optimal operation
 * 
 * Subclasses must implement the abstract methods to provide system-specific
 * data retrieval and generation logic. The class handles the overall workflow
 * and common operations like scenario creation, notification management, and
 * data persistence.
 * 
 * @see androidx.work.Worker for background task execution
 * @see ToutcRepository for data persistence operations
 */
public abstract class AbstractGenerationWorker extends Worker {
    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String LP = "LP";
    public static final String INV = "INV";
    public static final String PAN = "PAN";
    public static final String PAN_D = "PAN_D";
    public static final String BAT = "BAT";
    public static final String BAT_SCH = "BAT_SCH";
    public static final String FROM = "FROM";
    public static final String TO = "TO";
    public static final String PANEL_COUNTS = "PANEL_COUNTS";
    public static final String MPPT_COUNT = "MPPT_COUNT";
    public static final String PROGRESS = "PROGRESS";
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int mNotificationId = 2;
    protected final ToutcRepository mToutcRepository;
    protected NotificationManager mNotificationManager;
    private boolean mStopped = false;

    /**
     * Constructor for AbstractGenerationWorker.
     * 
     * Initializes the worker with necessary dependencies for data generation and
     * user notification. Creates the notification channel for progress updates
     * and sets up the repository for data persistence operations.
     * 
     * @param context The application context
     * @param workerParams Parameters passed to the worker including input data
     */
    public AbstractGenerationWorker(@NonNull Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
    }

    /**
     * Handles worker cancellation by setting the stopped flag.
     * 
     * This method is called by the WorkManager when the worker is stopped
     * or cancelled. It sets an internal flag that can be checked during
     * long-running operations to enable graceful cancellation.
     */
    @Override
    public void onStopped() {
        super.onStopped();
        mStopped = true;
    }

    /**
     * Main worker execution method that orchestrates the generation process.
     * 
     * This method performs the complete energy system data generation workflow:
     * 1. Extracts input parameters from the work request
     * 2. Creates a new scenario to contain the generated data
     * 3. Generates load profile data from historical consumption 
     * 4. Creates inverter specifications based on system parameters
     * 5. Generates solar panel data with realistic generation curves
     * 6. Creates battery system data and charging schedules
     * 
     * The method is designed to handle partial generation based on input flags,
     * allowing selective generation of specific components. Progress is reported
     * throughout the process via notifications.
     * 
     * @return Result.success() if generation completes successfully, Result.failure() otherwise
     */
    @NonNull
    @Override
    public Result doWork() {
        System.out.println("GenerationWorker:doWork invoked ");

        // Extract input parameters from the work request data
        Data inputData = getInputData();
        String mSystemSN = inputData.getString(KEY_SYSTEM_SN);
        boolean mLP = inputData.getBoolean(LP, false);      // Load Profile
        boolean mINV = inputData.getBoolean(INV, false);    // Inverter
        boolean mPNL = inputData.getBoolean(PAN, false);    // Panels
        boolean mPNLD = inputData.getBoolean(PAN_D, false); // Panel Data
        boolean mBAT = inputData.getBoolean(BAT, false);    // Battery
        boolean mBATS = inputData.getBoolean(BAT_SCH, false); // Battery Schedules
        int mpptCount = inputData.getInt(MPPT_COUNT, 0);
        String mFrom = inputData.getString(FROM);
        String mTo = inputData.getString(TO);
        
        // Parse panel configuration - comma-separated panel counts per string
        List<Integer> mStringPanelCount = Arrays.stream(
                        Objects.requireNonNull(inputData.getString(PANEL_COUNTS)).split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        // Retrieve system-specific data (power ratings, specifications)
        SystemData theSystemData = getSystemData(mSystemSN);

        // Validate mandatory parameters before proceeding
        if (null == mToutcRepository) return Result.failure();
        if (null == mSystemSN) return Result.failure();

        // Prepare scenario management - ensure unique naming
        List<Scenario> scenarios = mToutcRepository.getScenarios();
        List<String> mScenarioNames = new ArrayList<>();
        for (Scenario scenario : scenarios) mScenarioNames.add(scenario.getScenarioName());

        long createdLoadProfileID = 0;

        // Create a new scenario to contain all generated data
        report(getString(R.string.creating_usage));
        ScenarionKeys scenarioKeys = generateScenario(mSystemSN, mScenarioNames);

        Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup = null;

        // Generate load profile if requested - forms the basis of consumption patterns
        if (mLP) {
            createdLoadProfileID = generateLoadProfile(mSystemSN, mFrom, mTo, scenarioKeys.assignedScenarioID);
        }

        // Generate detailed load profile data for consumption modeling
        if (mLP) dbLookup = generateLoadProfileData(mSystemSN, mFrom, mTo, createdLoadProfileID);

        // Generate inverter specifications based on system parameters
        if (mINV && mLP)
            generateInverter(mSystemSN, theSystemData, mpptCount, scenarioKeys.assignedScenarioID);

        // Generate solar panel data with realistic generation curves
        if (mPNL && mINV && mLP) {
            // Calculate total system capacity and panel distribution
            double totalPV = theSystemData.popv * 1000;
            int totalPanelCount = 0;
            for (int i = 0; i < mpptCount; i++) totalPanelCount += mStringPanelCount.get(i);
            
            // Generate data for each MPPT string
            for (int i = 0; i < mpptCount; i++) {
                PanelSet panelSet = getPanelSet(mStringPanelCount, i, mSystemSN, totalPV, totalPanelCount, scenarioKeys.assignedScenarioID);

                // Generate detailed panel generation data if requested
                if (mPNLD)
                    generatePanelData((double) panelSet.stringSize, totalPanelCount, panelSet.panelID, dbLookup);
            }
        }

        // Generate battery system data and schedules
        if (mBAT) {
            generateBattery(theSystemData, mSystemSN, scenarioKeys.assignedScenarioID);
            // Generate battery charging schedules for optimization
            if (mBATS)
                generateBatterySchedules(mSystemSN, mFrom, mTo, scenarioKeys.assignedScenarioID);
        }

        // Complete the generation process
        report(getString(R.string.completed, scenarioKeys.finalScenarioName));

        // Clean up notification if worker was stopped
        if (mStopped) mNotificationManager.cancel(mNotificationId);
        return Result.success();
    }

    /**
     * Creates a new scenario with a unique name for the generated data.
     * 
     * This method ensures that each generation run creates a distinctly named scenario
     * to avoid conflicts with existing scenarios. It appends a numeric suffix if the
     * base system serial number is already in use as a scenario name.
     * 
     * @param mSystemSN The system serial number to use as the base scenario name
     * @param mScenarioNames List of existing scenario names to check for conflicts
     * @return ScenarionKeys containing the final unique name and assigned database ID
     */
    @NonNull
    private ScenarionKeys generateScenario(String mSystemSN, List<String> mScenarioNames) {
        Scenario scenario = new Scenario();
        String scenarioName = mSystemSN;
        int suffix = 1;
        
        // Ensure scenario name uniqueness by appending numeric suffix if needed
        while (mScenarioNames.contains(scenarioName)) {
            scenarioName = scenarioName + "_" + suffix;
            suffix++;
        }
        String finalScenarioName = scenarioName;
        scenario.setScenarioName(scenarioName);
        
        // Create scenario with empty component placeholders
        ScenarioComponents scenarioComponents = new ScenarioComponents(scenario,
                null, null, null, null, null,
                null, null, null, null, null, null);
        long assignedScenarioID = mToutcRepository.insertScenarioAndReturnID(scenarioComponents, false);
        return new ScenarionKeys(finalScenarioName, assignedScenarioID);
    }

    /**
     * Generates a load profile from historical consumption data.
     * 
     * This method creates a load profile by analyzing historical consumption patterns
     * and generating statistical distributions for different time periods (hourly, daily,
     * monthly). The load profile serves as the foundation for energy consumption modeling
     * in the generated scenario.
     * 
     * Key components:
     * - Hourly distribution: Shows consumption patterns throughout the day
     * - Daily distribution: Shows consumption patterns across days of the week  
     * - Monthly distribution: Shows seasonal consumption variations
     * - Base load: Minimum continuous consumption level
     * 
     * @param mSystemSN The system serial number for data retrieval
     * @param mFrom Start date for historical data analysis
     * @param mTo End date for historical data analysis
     * @param assignedScenarioID The scenario ID to associate with the load profile
     * @return The database ID of the created load profile
     */
    private long generateLoadProfile(String mSystemSN, String mFrom, String mTo, long assignedScenarioID) {
        long createdLoadProfileID;
        report(getString(R.string.gen_load_profile));
        
        // Retrieve historical consumption data aggregated by different time periods
        List<IntervalRow> hourly = mToutcRepository.getSumHour(mSystemSN, mFrom, mTo);
        List<IntervalRow> weekly = mToutcRepository.getSumDOW(mSystemSN, mFrom, mTo);
        List<IntervalRow> monthly = mToutcRepository.getAvgMonth(mSystemSN, mFrom, mTo);
        Double baseLoad = mToutcRepository.getBaseLoad(mSystemSN, mFrom, mTo);

        // Calculate total load for percentage distribution calculations
        Double totalLoad = 0D;
        for (IntervalRow row : weekly) totalLoad += row.load;

        // Build the load profile with consumption characteristics
        LoadProfile loadProfile = new LoadProfile();
        loadProfile.setAnnualUsage(totalLoad);
        loadProfile.setDistributionSource(mSystemSN);
        loadProfile.setHourlyBaseLoad(baseLoad);
        
        // Create hourly distribution showing consumption patterns throughout the day
        HourlyDist hd = new HourlyDist();
        List<Double> hourOfDayDist = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            Double hv = hourly.get(i).load;
            hourOfDayDist.add((hv / totalLoad) * 100);
        }
        hd.dist = hourOfDayDist;
        loadProfile.setHourlyDist(hd);
        DOWDist dd = new DOWDist();
        List<Double> dowDist = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Double dv = weekly.get(i).load;
            dowDist.add((dv / totalLoad) * 100);
        }
        dd.dowDist = dowDist;
        loadProfile.setDowDist(dd);
        MonthlyDist md = new MonthlyDist();
        List<Double> moyDist = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Double mv = monthly.get(i).load;
            moyDist.add((mv / totalLoad) * 100);
        }
        md.monthlyDist = moyDist;
        loadProfile.setMonthlyDist(md);
        createdLoadProfileID = mToutcRepository.saveLoadProfileAndReturnID(assignedScenarioID, loadProfile);
        return createdLoadProfileID;
    }

    @NonNull
    private Map<Integer, Map<String, AlphaESSTransformedData>> generateLoadProfileData(
            String mSystemSN, String mFrom, String mTo, long createdLoadProfileID) {
        Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup;
        List<AlphaESSTransformedData> dbRows;
        report(getString(R.string.adding_data));

        dbRows = mToutcRepository.getAlphaESSTransformedData(mSystemSN, mFrom, mTo);
        dbRows = expandHoursIfNeeded(dbRows);
        dbLookup = new HashMap<>();
        for (AlphaESSTransformedData dbRow : dbRows) {
            LocalDate dbDate = LocalDate.parse(dbRow.getDate(), DATE_FORMAT);
            String dbTime = dbRow.getMinute();
            Map<String, AlphaESSTransformedData> entry = dbLookup.get(dbDate.getDayOfYear());
            if (null == entry) {
                entry = new HashMap<>();
                entry.put(dbTime, dbRow);//(dbTimeLT.format(MIN_FORMAT), dbRow);
                dbLookup.put(dbDate.getDayOfYear(), entry);
            } else entry.put(dbTime, dbRow);
        }
        report("Loaded load data");

        ArrayList<LoadProfileData> rows = new ArrayList<>();
        LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
        while (active.isBefore(end)) {
            LoadProfileData row = new LoadProfileData();
            row.setDo2001(active.getDayOfYear());
            row.setLoadProfileID(createdLoadProfileID);
            row.setDate(active.format(DATE_FORMAT));
            row.setMinute(active.format(MIN_FORMAT));
            row.setDow(active.getDayOfWeek().getValue());
            row.setMod(active.getHour() * 60 + active.getMinute());
            // Not every 5 minute interval has data uploaded to AlphaESS
            Integer doy = active.getDayOfYear();
            String hhmm = row.getMinute();
            double loadToSet = 0D;
            Map<String, AlphaESSTransformedData> aDay = dbLookup.get(doy);
            if (!(null == aDay)) {
                AlphaESSTransformedData a5MinutePeriod = aDay.get(hhmm);
                if (!(null == a5MinutePeriod)) {
                    loadToSet = a5MinutePeriod.getLoad();
                }
            }
            row.setLoad(loadToSet);

            rows.add(row);
            active = active.plusMinutes(5);
        }
        report("Storing data");

        mToutcRepository.createLoadProfileDataEntries(rows);
        report("Stored data");
        return dbLookup;
    }

    protected List<AlphaESSTransformedData> expandHoursIfNeeded(List<AlphaESSTransformedData> dbRows) {
        return dbRows;
    }

    private void generateInverter(String mSystemSN, SystemData theSystemData, int mpptCount, long assignedScenarioID) {
        Inverter inverter = new Inverter();
        inverter.setInverterName(mSystemSN);
        inverter.setMinExcess(0.008D);
        inverter.setMaxInverterLoad(theSystemData.poinv);
        inverter.setMpptCount(mpptCount);
        int loss = getLoss(mSystemSN);
        inverter.setAc2dcLoss(loss);
        inverter.setDc2acLoss(loss);
        inverter.setDc2dcLoss(0);

        mToutcRepository.saveInverter(assignedScenarioID, inverter);
        report("Stored inverter");
    }

    abstract protected int getLoss(String mSystemSN);

    @NonNull
    private PanelSet getPanelSet(List<Integer> mStringPanelCount, int i, String mSystemSN, double totalPV, int totalPanelCount, long assignedScenarioID) {
        Integer stringSize = mStringPanelCount.get(i);
        Panel panel = new Panel();
        panel.setPanelCount(stringSize);
        panel.setPanelName("String-" + (i + 1));
        panel.setInverter(mSystemSN);
        panel.setMppt(i + 1);
        panel.setPanelkWp((int) (totalPV / totalPanelCount));
        long panelID = mToutcRepository.savePanel(assignedScenarioID, panel);
        return new PanelSet(stringSize, panelID);
    }

    /**
     * Generates panel data with realistic solar generation patterns.
     * 
     * This method creates synthetic solar panel generation data by scaling actual
     * historical PV generation data according to the string size and total panel
     * count. It generates data points for every 5-minute interval throughout a
     * full year (2001 reference year) to provide comprehensive generation modeling.
     * 
     * The method handles missing data gracefully by setting PV output to zero
     * when no historical data is available for a given time period.
     * 
     * @param stringSize Number of panels in this specific string
     * @param totalPanelCount Total number of panels across all strings
     * @param panelID Database ID of the panel configuration
     * @param dbLookup Historical generation data lookup table indexed by day and time
     */
    private void generatePanelData(double stringSize, double totalPanelCount, long panelID, Map<Integer, Map<String, AlphaESSTransformedData>> dbLookup) {
        report("Loaded raw data");

        // Calculate this string's proportion of total system generation
        double proportionOfPV = stringSize / totalPanelCount;

        ArrayList<PanelData> rows = new ArrayList<>();
        
        // Generate data for full year (2001 used as reference year)
        LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 1);
        LocalDateTime dbActive = active.plusMinutes(-1);  // Data is offset by 1 minute
        LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
        
        // Generate data points for every 5-minute interval throughout the year
        while (active.isBefore(end)) {
            PanelData row = new PanelData();
            row.setDo2001(active.getDayOfYear());
            row.setPanelID(panelID);
            row.setDate(active.format(DATE_FORMAT));
            row.setMinute(active.format(MIN_FORMAT));
            row.setDow(active.getDayOfWeek().getValue());
            row.setMod(active.getHour() * 60 + active.getMinute());
            
            // Retrieve historical PV data for this time period
            // Note: Solar data is offset by one minute from load data
            Integer doy = dbActive.getDayOfYear();
            String hhmm = dbActive.format(MIN_FORMAT);
            double pvToSet = 0D;
            
            // Look up historical generation data and scale by string proportion
            Map<String, AlphaESSTransformedData> aDay = dbLookup.get(doy);
            if (!(null == aDay)) {
                AlphaESSTransformedData a5MinutePeriod = aDay.get(hhmm);
                if (!(null == a5MinutePeriod)) {
                    pvToSet = a5MinutePeriod.getPv() * proportionOfPV;
                }
            }
            row.setPv(pvToSet);

            rows.add(row);
            active = active.plusMinutes(5);
            dbActive = dbActive.plusMinutes(5);
        }
        report("Storing PV data");

        mToutcRepository.savePanelData(rows);
    }

    abstract protected void generateBattery(SystemData theSystemData, String mSystemSN, long assignedScenarioID);

    abstract protected void generateBatterySchedules(String mSystemSN, String mFrom, String mTo, long assignedScenarioID);

    @NonNull
    protected abstract SystemData getSystemData(String mSystemSN);

    /**
     * Retrieves a localized string resource.
     * 
     * @param resource_id The string resource identifier
     * @return The localized string value
     */
    private String getString(int resource_id) {
        return getApplicationContext().getString(resource_id);
    }

    /**
     * Retrieves a localized string resource with template substitution.
     * 
     * @param resource_id The string resource identifier
     * @param templateValue The value to substitute in the template string
     * @return The localized string with substituted value
     */
    private String getString(int resource_id, String templateValue) {
        return getApplicationContext().getString(resource_id, templateValue);
    }

    /**
     * Reports progress to the user via notifications and work manager.
     * 
     * This method provides dual progress reporting: updating the WorkManager
     * progress state for programmatic monitoring and displaying a user notification
     * showing the current operation status.
     * 
     * @param theReport The progress message to display
     */
    protected void report(String theReport) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, theReport).build());
        mNotificationManager.notify(mNotificationId, getNotification(theReport));
    }

    /**
     * Creates a notification for progress reporting.
     * 
     * This method builds an ongoing notification that shows the current generation
     * progress to the user. The notification includes a cancel action that allows
     * users to stop the generation process if needed. The notification is designed
     * to be non-intrusive (silent) while still providing visible progress feedback.
     * 
     * @param progress The current progress message to display
     * @return A configured Notification for display
     */
    @NonNull
    private Notification getNotification(@NonNull String progress) {
        Context context = getApplicationContext();
        String id = context.getString(R.string.alphaess_channel_id);
        String title = context.getString(R.string.generate_usage);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        
        // Create cancel action that allows users to stop the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        return new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_content_copy_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                // Add cancel action for user control
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();
    }

    /**
     * Creates the Android notification channel for progress updates.
     * 
     * This method initializes the notification channel required for displaying
     * progress notifications on Android 8.0+ devices. The channel is configured
     * with default importance to provide visible but non-intrusive notifications
     * during the generation process.
     */
    private void createChannel() {
        CharSequence name = getApplicationContext().getString(R.string.alphaess_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.alphaess_channel_id), name, importance);
        channel.setDescription(description);
        
        // Register the channel with the system
        // Note: Channel importance and behavior cannot be changed after registration
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Data holder for scenario creation results.
     * 
     * This private static class encapsulates the results of scenario creation,
     * providing both the final unique scenario name and the database ID assigned
     * to the scenario. This information is needed throughout the generation process
     * to associate generated components with the correct scenario.
     */
    private static class ScenarionKeys {
        public final String finalScenarioName;
        public final long assignedScenarioID;

        public ScenarionKeys(String finalScenarioName, long assignedScenarioID) {
            this.finalScenarioName = finalScenarioName;
            this.assignedScenarioID = assignedScenarioID;
        }
    }

    /**
     * Data holder for solar panel string configuration.
     * 
     * This private static class represents a specific solar panel string within
     * a multi-string solar installation. It links the physical string size
     * (number of panels) with the database identifier for the panel configuration.
     * This association is essential for generating accurate panel-specific data.
     */
    private static class PanelSet {
        public final Integer stringSize;
        public final long panelID;

        public PanelSet(Integer stringSize, long panelID) {
            this.stringSize = stringSize;
            this.panelID = panelID;
        }
    }

    /**
     * Data container for energy system specifications.
     * 
     * This public static class holds the core electrical specifications of an energy
     * system that are used throughout the generation process. These values define
     * the system's capabilities and are used to scale generated data appropriately.
     * 
     * The class is public to allow access from subclasses that need to retrieve
     * and populate system-specific data from external sources.
     */
    public static class SystemData {
        /** Peak power output of photovoltaic system in kW */
        public double popv;
        /** Peak power output of inverter in kW */
        public double poinv;
        /** List of surplus battery capacity values for different scenarios */
        public List<Double> surplusCobat;

        public SystemData(double popv, double poinv, List<Double> surplusCobat) {
            this.popv = popv;
            this.poinv = poinv;
            this.surplusCobat = surplusCobat;
        }

    }
}