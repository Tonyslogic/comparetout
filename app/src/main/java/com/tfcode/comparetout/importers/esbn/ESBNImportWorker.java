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

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.tfcode.comparetout.importers.esbn.ImportESBNOverview.ESBN_PREVIOUS_SELECTED_KEY;
import static com.tfcode.comparetout.importers.esbn.ImportESBNOverview.ESBN_SYSTEM_LIST_KEY;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.esbn.responses.ESBNException;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.ui2.UserTimezoneStore;

import java.time.ZoneId;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.ui2.UI2NotificationLaunch;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Single;

public class ESBNImportWorker extends Worker {

    private final ToutcRepository mToutcRepository;
    private final NotificationManager mNotificationManager;
    // Distinct notification slot per worker class — see
    // plans/eventual-bouncing-hare.md.
    private static final int mNotificationId = 3;
    private boolean mStopped = false;
    private boolean mUseUI2 = false;
    private String mSelectedSysSn = null;
    private long mLastNotifyAt = 0L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 250L;


    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");


    public static final String KEY_SYSTEM_SN = "KEY_SYSTEM_SN";
    public static final String KEY_URI = "KEY_URI";

    public static final String PROGRESS = "PROGRESS";

    public ESBNImportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        createChannel();
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting import from file").build());
        System.out.println("Import worker created");
    }

    @Override
    public void onStopped(){
        super.onStopped();
        mStopped = true;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("ESBNImportWorker:doWork invoked ");
        Data inputData = getInputData();
        String systemSN = inputData.getString(KEY_SYSTEM_SN);
        String uriString = inputData.getString(KEY_URI);
        Uri fileUri = Uri.parse(uriString);
        mSelectedSysSn = systemSN;
        mUseUI2 = UI2NotificationLaunch.isUI2Enabled(getApplicationContext());

        publishProgress("Importing energy", true);
        
        // Do some work
        Map<LocalDateTime, Pair<Double, Double>> timeAlignedEntries = new HashMap<>();
        AtomicReference<LocalDateTime> last = new AtomicReference<>(LocalDateTime.of(1970,1, 1, 0, 0));
        try (InputStream is = getApplicationContext().getContentResolver().openInputStream(fileUri)){
            String mprnFromFile = ESBNHDFClient.readEntriesFromFile(is, (calc, type, ldt, value) -> {
                Pair<Double, Double> importExport = timeAlignedEntries.get(ldt);
                if (ldt.isAfter(last.get())) last.set(ldt);
                switch (type) {
                    case IMPORT:
                        if ((null == importExport))
                            timeAlignedEntries.put(ldt, new Pair<>(value/(calc ? 1D : 2D), 0D));
                        else
                            timeAlignedEntries.put(ldt, new Pair<>(value/(calc ? 1D : 2D), importExport.second));
                        break;
                    case EXPORT:
                        if ((null == importExport))
                            timeAlignedEntries.put(ldt, new Pair<>(0D, value/(calc ? 1D : 2D)));
                        else
                            timeAlignedEntries.put(ldt, new Pair<>(importExport.first, value/(calc ? 1D : 2D)));
                        break;
                }
            });
            if (!(mprnFromFile.isEmpty())) {
                // Update the stored list of mprns
                List<String> mSerialNumbers = new ArrayList<>();
                TOUTCApplication application = (TOUTCApplication) getApplicationContext();
                Preferences.Key<String> systemList = PreferencesKeys.stringKey(ESBN_SYSTEM_LIST_KEY);
                Single<String> value4 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(systemList)).onErrorReturnItem("[]");
                String systemListJsonString =  value4.blockingGet();
                List<String> mprnListFromPreferences =
                        new Gson().fromJson(systemListJsonString, new TypeToken<List<String>>(){}.getType());
                if (!(null == mprnListFromPreferences) && !(mprnListFromPreferences.isEmpty())) {
                    mSerialNumbers.addAll(mprnListFromPreferences);
                }
                if (!(mSerialNumbers.contains(mprnFromFile))) {
                    mSerialNumbers.add(mprnFromFile);
                    String stringResponse = new Gson().toJson(mSerialNumbers);
                    boolean x = application.putStringValueIntoDataStore(ESBN_SYSTEM_LIST_KEY, stringResponse);
                    if (!x) System.out.println("ESBNImportWorker::doWork, failed to store list");
                    x = application.putStringValueIntoDataStore(ESBN_PREVIOUS_SELECTED_KEY, mprnFromFile);
                    if (!x) System.out.println("ESBNImportWorker::doWork, failed to update previously selected");
                }
                // update the mprn that is used in the DB inserts
                systemSN = mprnFromFile;
            }
        } catch (IOException | ESBNException e) {
            e.printStackTrace();
        }
        // Check and Remove the last day if missing more than 18 entries
        LocalDate lastDay = last.get().toLocalDate();
        int count = 0;
        for (LocalDateTime key : timeAlignedEntries.keySet()) {
            if (key.toLocalDate().equals(lastDay)) {
                count++;
            }
        }
        if (count < 31) {
            timeAlignedEntries.entrySet().removeIf(entry -> entry.getKey().toLocalDate().equals(lastDay));
        }
        // Store transformed data. ESBN HDF read-times are local wall-clock; interpret them in the saved zone
        // to stamp the canonical UTC millis (Phase 1, timezone-and-rollout.md). The date/minute strings stay
        // the wall-clock the file provided (already the source's local time), which is what Compare renders.
        ZoneId zone = UserTimezoneStore.resolvedZone(getApplicationContext());
        List<AlphaESSTransformedData> normalizedEntityList = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Pair<Double, Double>> entry: timeAlignedEntries.entrySet()) {
            AlphaESSTransformedData dbEntry = new AlphaESSTransformedData();
            dbEntry.setBuy(entry.getValue().first);
            dbEntry.setFeed(entry.getValue().second);
            dbEntry.setPv(0D);
            dbEntry.setLoad(0D);
            dbEntry.setSysSn(systemSN != null ? systemSN : "Not set");
            dbEntry.setDate(entry.getKey().format(DATE_FORMAT));
            dbEntry.setMinute(entry.getKey().format(MIN_FORMAT));
            dbEntry.setMillisSinceEpoch(entry.getKey().atZone(zone).toInstant().toEpochMilli());
            normalizedEntityList.add(dbEntry);
        }
        mToutcRepository.addTransformedData(normalizedEntityList);

        // The MPRN may only be known after parsing the file — refresh the
        // cached SN so the completion notification deep-links to the right
        // source under UI2.
        mSelectedSysSn = systemSN;
        publishProgress("All done importing " + systemSN, true);

        if (mStopped) mNotificationManager.cancel(mNotificationId);

        return Result.success();
    }

    /**
     * Publish the worker's progress to WorkManager + the notification
     * shade. NotificationManager.notify is thread-safe, so no main-thread
     * hop is required (the earlier handler.post approach raced the worker's
     * own completion). [force]=true bypasses the in-loop throttle and is
     * used for the first/last updates so terminal state always lands.
     */
    private void publishProgress(@NonNull String progress, boolean force) {
        setProgressAsync(new Data.Builder().putString(PROGRESS, progress).build());
        long now = System.currentTimeMillis();
        if (force || now - mLastNotifyAt > MIN_NOTIFY_INTERVAL_MS) {
            mLastNotifyAt = now;
            mNotificationManager.notify(mNotificationId, getNotification(progress));
        }
    }

    @NonNull
    private Notification getNotification(@NonNull String progress) {
        // Build a notification using bytesRead and contentLength

        Context context = getApplicationContext();
        String id = context.getString(R.string.esbn_channel_id);
        String title = context.getString(R.string.esbn_import_notification_title);
        String cancel = context.getString(R.string.cancel_fetch_alpha);
        // This PendingIntent can be used to cancel the worker
        PendingIntent intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        PendingIntent activityPendingIntent = UI2NotificationLaunch.contentIntent(
                context, mUseUI2, ComparisonUIViewModel.Importer.ESBNHDF,
                mSelectedSysSn, ImportESBNActivity.class);

        return new NotificationCompat.Builder(context, id)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_baseline_download_24)
                .setOngoing(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(activityPendingIntent)
                // Add the cancel action to the notification which can
                // be used to cancel the worker
                .addAction(android.R.drawable.ic_delete, cancel, intent)
                .build();
    }

    private void createChannel() {
        // Create a Notification channel
        CharSequence name = getApplicationContext().getString(R.string.esbn_channel_name);
        String description = getApplicationContext().getString(R.string.channel_description);
        // DEFAULT (not LOW): builders silence via setSilent(true); a below-DEFAULT "silent" channel hides the status-bar icon
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(
                getApplicationContext().getString(R.string.esbn_channel_id), name, importance);
        channel.setDescription(description);
        // Register the channel with the system. You can't change the importance
        // or other notification behaviors after this.
        NotificationManager notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }
}
