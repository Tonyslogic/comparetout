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

package com.tfcode.comparetout.scenario.loadprofile;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ListenableWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class GenerateMissingLoadDataWorker extends Worker {

    private final ToutcRepository mToutcRepository;

    public GenerateMissingLoadDataWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @NonNull
    @Override
    public ListenableWorker.Result doWork() {
        List<Long> missing = mToutcRepository.checkForMissingLoadProfileData();

        Context context = getApplicationContext();
        String  title= context.getString(R.string.load_gen_notification_title);
        String text = context.getString(R.string.load_gen_notification_text);

        if (!missing.isEmpty()) {
            // NOTIFICATION SETUP
            int notificationId = 1;
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
            builder.setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.house)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setTimeoutAfter(30000)
                    .setSilent(true);
            // Issue the initial notification with zero progress
            int PROGRESS_MAX = 100;
            int PROGRESS_CURRENT = 0;
            int PROGRESS_CHUNK = PROGRESS_MAX;
            if (!missing.isEmpty()) {
                PROGRESS_CHUNK = PROGRESS_MAX / missing.size();
                builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                notificationManager.notify(notificationId, builder.build());
            }

            for (long loadProfileID : missing) {
                builder.setContentText("Generating data");
                notificationManager.notify(notificationId, builder.build());

                LoadProfile mLoadProfile = mToutcRepository.getLoadProfileWithLoadProfileID(loadProfileID);
                ArrayList<LoadProfileData> rows = new ArrayList<>();
                LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
                LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter minFormat = DateTimeFormatter.ofPattern("HH:mm");
                while (active.isBefore(end)) {
                    LoadProfileData row = new LoadProfileData();
                    row.setDo2001(active.getDayOfYear());
                    row.setLoadProfileID(loadProfileID);
                    row.setDate(active.format(dateFormat));
                    row.setMinute(active.format(minFormat));
                    row.setDow(active.getDayOfWeek().getValue());
                    row.setMod(active.getHour() * 60 + active.getMinute());
                    row.setLoad(genLoad(mLoadProfile, active.getMonth().getValue(),
                            active.getDayOfWeek().getValue(), active.getHour()));
                    rows.add(row);
                    active = active.plusMinutes(5);
                }

                builder.setContentText("Saving data");
                notificationManager.notify(notificationId, builder.build());

                mToutcRepository.createLoadProfileDataEntries(rows);

                // NOTIFICATION PROGRESS
                PROGRESS_CURRENT += PROGRESS_CHUNK;
                builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                notificationManager.notify(notificationId, builder.build());
            }

            if (!missing.isEmpty()) {
                // NOTIFICATION COMPLETE
                builder.setContentText("Generation complete")
                        .setProgress(0, 0, false);
                notificationManager.notify(notificationId, builder.build());
            }
        }

        return ListenableWorker.Result.success();
    }

    private double genLoad(LoadProfile loadProfile, int month, int dow, int hod) {
        double load;
        int totalXXXDaysInMonth = countDayOccurrenceInMonth(DayOfWeek.of(dow), YearMonth.of(2001, month));

        double distMonth = loadProfile.getMonthlyDist().monthlyDist.get(month -1)/100d;
        if (dow == 7) dow = 0; //Index 0 is used for Sun in the loadProfile...dowDist
        double distDOW = loadProfile.getDowDist().dowDist.get(dow)/100d;
        double distHOD = loadProfile.getHourlyDist().dist.get(hod)/100d;

        double monthUse = loadProfile.getAnnualUsage() * distMonth;
        double dayUse = (monthUse / (double)totalXXXDaysInMonth) * distDOW;
        double hourUse = dayUse * distHOD;

        load = hourUse/12d;

        return load;
    }

    public static int countDayOccurrenceInMonth(DayOfWeek dow, YearMonth month) {
        LocalDate start = month.atDay(1).with(TemporalAdjusters.nextOrSame(dow));
        return (int) ChronoUnit.WEEKS.between(start, month.atEndOfMonth()) + 1;
    }
}
