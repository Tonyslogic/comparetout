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

package com.tfcode.comparetout.scenario.panel;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.json.scenario.pgvis.Hourly;
import com.tfcode.comparetout.model.json.scenario.pgvis.PvGISData;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;
import com.tfcode.comparetout.scenario.sim.SimTime;
import com.tfcode.comparetout.util.ContractFileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

public class PVGISLoader extends Worker {

    private final ToutcRepository mToutcRepository;
    private final static Double MAGIC_NUMBER = 919821d;
    private final Context mContext;
    private static final String TAG = "PVGISLoader";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter PVGIS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd:HHmm");

    public PVGISLoader(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Long panelID = getInputData().getLong("panelID", 0);
        String folderUriString = getInputData().getString("folderUri");

        Context context = getApplicationContext();
        String title= context.getString(R.string.pvgis_notification_title);
        String text = context.getString(R.string.pvgis_notification_text);

        Panel mPanel = mToutcRepository.getPanelForID(panelID);
        DecimalFormat df = new DecimalFormat("#.000");
        String latitude = df.format(mPanel.getLatitude());
        String longitude = df.format(mPanel.getLongitude());
        String filename = "PVGIS(" + latitude + ")(" + longitude +
                ")(" + mPanel.getSlope() + ")(" + mPanel.getAzimuth() + ")" ;

        // NOTIFICATION SETUP
        int notificationId = 1;
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_baseline_save_24)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTimeoutAfter(30000)
                .setSilent(true);

        Uri folderUri = Uri.parse(folderUriString);
        Uri fileUri = ContractFileUtils.findFileInFolderTree(mContext, folderUri, filename);
        if (null == fileUri){
            Log.i(TAG, "Unable to find the file");
            builder.setContentText("Cannot find file to load")
                    .setProgress(0, 0, false);
            sendNotification(notificationManager, notificationId, builder);
            return Result.success();
        }

        InputStream inputStream;
        try{

            int PROGRESS_MAX = 100;
            int PROGRESS_CURRENT = 0;// Issue the initial notification with zero progress
            builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
            sendNotification(notificationManager, notificationId, builder);

            inputStream = mContext.getContentResolver().openInputStream(fileUri);
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

            builder.setProgress(PROGRESS_MAX, 30, false);
            builder.setContentText("Data read");
            sendNotification(notificationManager, notificationId, builder);

            Type type = new TypeToken<PvGISData>(){}.getType();
            PvGISData pvGISData = new Gson().fromJson(reader, type);
            reader.close();

            ArrayList<PanelData> panelDataList = new ArrayList<>();
            for (Hourly pp : pvGISData.hourlies.hourlies) {
                panelDataList.addAll(mapHourlyTo2001Rows(mPanel.getPanelIndex(), pp.time, pp.gi,
                        mPanel.getPanelCount(), mPanel.getPanelkWp()));
            }

            builder.setProgress(PROGRESS_MAX, 60, false);
            builder.setContentText("Data formatted, storing...");
            sendNotification(notificationManager, notificationId, builder);

            mToutcRepository.savePanelData(panelDataList);

            // NOTIFICATION COMPLETE
            builder.setContentText("DB update complete")
                    .setProgress(0, 0, false);
            sendNotification(notificationManager, notificationId, builder);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Result.retry();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Result.success();
    }

    /**
     * Maps one PVGIS hourly reading onto the synthetic 2001 UTC grid as up to twelve 5-minute
     * {@link PanelData} rows. PVGIS timestamps are UTC (SARAH/ERA5 per the PVGIS docs) but carry a native
     * minute offset (e.g. {@code HH:11} past the hour). The hour is therefore truncated to its top
     * ({@code HH:00}) before being expanded into the twelve {@code :00,:05,…,:55} slots, so the PV rows sit
     * on EXACTLY the same {@code :00}-based 2001 grid as the load: the simulation merges PV onto the load by
     * UTC millis, and a stamp offset would land PV on an instant the load never has — silently dropping all
     * of it. (No device-zone conversion and no hour fudge; the year is remapped to 2001, month/day/hour
     * kept.) Feb 29 of a leap source year is dropped (2001 is non-leap) so the PV row count stays equal to
     * the load's 105120; an hourly that falls entirely on Feb 29 therefore yields no rows. Pure
     * (Android-free) so it can be unit-tested directly.
     *
     * @param panelIndex the owning panel's index (stored on each row)
     * @param pvgisTime  the PVGIS {@code time} field, formatted {@code yyyyMMdd:HHmm} (UTC)
     * @param giWhm2     the PVGIS {@code G(i)} global irradiance for the hour
     * @param panelCount the panel count for this PV string
     * @param panelkWp   the per-panel peak power (kWp)
     * @return the 5-minute {@link PanelData} rows for this hour, on the 2001 UTC grid
     */
    static java.util.List<PanelData> mapHourlyTo2001Rows(long panelIndex, String pvgisTime, double giWhm2,
                                                         int panelCount, double panelkWp) {
        java.util.List<PanelData> rows = new ArrayList<>(12);
        // Snap to the top of the hour: PVGIS stamps the hourly value at a minute offset (e.g. HH:11), but the
        // load grid — and hence the millis-keyed PV/load merge — is on HH:00,:05,…,:55. Expanding from the raw
        // offset would land every PV row between load instants and drop all of it (see method javadoc).
        LocalDateTime utc = LocalDateTime.parse(pvgisTime, PVGIS_FORMAT).truncatedTo(ChronoUnit.HOURS);
        double pvPerInterval = (giWhm2 / 12d / MAGIC_NUMBER) * panelCount * panelkWp;
        for (int i = 0; i < 12; i++) {
            LocalDateTime slot = utc.plusMinutes(5L * i);
            if (slot.getMonthValue() == 2 && slot.getDayOfMonth() == 29) continue;
            LocalDateTime mapped = slot.withYear(2001);
            PanelData row = new PanelData();
            row.setPanelID(panelIndex);
            row.setDate(mapped.format(DATE_FORMAT));
            row.setMinute(mapped.format(MIN_FORMAT));
            row.setMod(mapped.getHour() * 60 + mapped.getMinute());
            row.setDow(mapped.getDayOfWeek().getValue());
            row.setDo2001(mapped.getDayOfYear());
            row.setMillisSinceEpoch(SimTime.toEpochMillis(mapped, ZoneOffset.UTC));
            row.setPv(pvPerInterval);
            rows.add(row);
        }
        return rows;
    }


    private void sendNotification(NotificationManagerCompat notificationManager, int notificationId, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(
                mContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(PVGISLoader.class.getName(), "Permission not granted to send notification");
            return;
        }
        notificationManager.notify(notificationId, builder.build());

    }

}
