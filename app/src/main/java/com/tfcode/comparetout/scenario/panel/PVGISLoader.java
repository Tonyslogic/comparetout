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
import com.tfcode.comparetout.util.ContractFileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class PVGISLoader extends Worker {

    private final ToutcRepository mToutcRepository;
    private final static Double MAGIC_NUMBER = 919821d;
    private final Context mContext;
    private static final String TAG = "PVGISLoader";

    public PVGISLoader(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @Override
    public void onStopped(){
        super.onStopped();
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
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter minFormat = DateTimeFormatter.ofPattern("HH:mm");

            DateTimeFormatter pvGisFormat = DateTimeFormatter.ofPattern("yyyyMMdd:HHmm");
            ZoneId localZone = ZoneOffset.systemDefault();

            for(Hourly pp : pvGISData.hourlies.hourlies){
                LocalDateTime saharaTZ = LocalDateTime.parse(pp.time, pvGisFormat);
                LocalDateTime active = saharaTZ.atZone(ZoneOffset.UTC).withZoneSameInstant(localZone).toLocalDateTime();
                active = active.minusHours(1);
                boolean shift = false;
                for (int i = 0; i < 12; i++) {
                    if (active.getYear() != saharaTZ.getYear()) {
                        active = active.plusYears(1);
                        shift = true;
                    }
                    PanelData row = new PanelData();
                    row.setDo2001(active.getDayOfYear());
                    row.setPanelID(mPanel.getPanelIndex());
                    row.setMinute(active.format(minFormat));
                    row.setDow(active.getDayOfWeek().getValue());
                    row.setMod(active.getHour() * 60 + active.getMinute());
                    row.setPv( (pp.gi / 12d / MAGIC_NUMBER) * mPanel.getPanelCount() * mPanel.getPanelkWp());
                    row.setDate(active.format(dateFormat));
                    panelDataList.add(row);
                    if (shift) {
                        active = active.minusYears(1);
                        shift = false;
                    }
                    active = active.plusMinutes(5);
                }
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


    private void sendNotification(NotificationManagerCompat notificationManager, int notificationId, NotificationCompat.Builder builder) {
        if (ActivityCompat.checkSelfPermission(
                mContext, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(PVGISLoader.class.getName(), "Permission not granted to send notification");
            return;
        }
        notificationManager.notify(notificationId, builder.build());

    }

}
