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

import static android.content.Context.NOTIFICATION_SERVICE;

import static com.tfcode.comparetout.importers.homeassistant.ImportHAOverview.HA_COBAT_KEY;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.AbstractGenerationWorker;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.LoadProfileData;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import io.reactivex.Single;

public class GenerationWorker extends AbstractGenerationWorker {

    public GenerationWorker(@NonNull Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        mNotificationManager = (NotificationManager)
                context.getSystemService(NOTIFICATION_SERVICE);
        setProgressAsync(new Data.Builder().putString(PROGRESS, "Starting GenerationWorker").build());
        System.out.println("GenerationWorker created");
    }
    @Override
    protected List<AlphaESSTransformedData> expandHoursIfNeeded(List<AlphaESSTransformedData> dbRows) {
        final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final DateTimeFormatter MIN_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
        final DateTimeFormatter DB_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd,HH:mm");
        List<AlphaESSTransformedData> expandedRows = new ArrayList<>();
        for (AlphaESSTransformedData dbRow : dbRows) {
            LocalDateTime date = LocalDateTime.parse(dbRow.getDate() + "," + dbRow.getMinute(), DB_FORMAT).minusHours(1);
            for (int i = 0; i < 60; i = i + 5) {
                AlphaESSTransformedData expandedRow = new AlphaESSTransformedData();
                expandedRow.setSysSn(dbRow.getSysSn());
                expandedRow.setDate(date.format(DATE_FORMAT));
                expandedRow.setMinute(date.format(MIN_FORMAT));
                expandedRow.setPv(dbRow.getPv()/12D);
                expandedRow.setLoad(dbRow.getLoad()/12D);
                expandedRow.setFeed(dbRow.getFeed()/12D);
                expandedRow.setBuy(dbRow.getBuy()/12D);
                expandedRows.add(expandedRow);
                date = date.plusMinutes(5);
            }
        }
        return expandedRows;
    }
    protected int getLoss(String mSystemSN) {
        // TODO: Implement this method
        return 4;
    }

    @NonNull
    @Override
    protected SystemData getSystemData(String mSystemSN) {
        double popv = mToutcRepository.getHAPopv(mSystemSN);
        double poinv = mToutcRepository.getHAPoinv(mSystemSN);
        TOUTCApplication application = (TOUTCApplication)getApplicationContext();
        Preferences.Key<String> systemList = PreferencesKeys.stringKey(HA_COBAT_KEY);
        Single<String> value4 = application.getDataStore()
                .data().firstOrError()
                .map(prefs -> prefs.get(systemList)).onErrorReturnItem("0.0");
        List<Double> estimatedBatteryCapacities =
                Arrays.stream(value4.blockingGet().split(","))
                        .map(Double::valueOf)
                        .collect(Collectors.toList());
        return new SystemData(popv, poinv, estimatedBatteryCapacities);
    }
    @Override
    protected void generateBattery(SystemData theSystemData, String mSystemSN, long assignedScenarioID) {
        for (Double coBat : theSystemData.surplusCobat) {
            Battery battery = new Battery();
            battery.setBatterySize((int)(coBat * 100) / 100D);
            battery.setStorageLoss(1);
            ChargeModel chargeModel = new ChargeModel();

            chargeModel.percent0 = 30;
            chargeModel.percent12 = 100;
            chargeModel.percent90 = 10;
            chargeModel.percent100 = 0;
            battery.setChargeModel(chargeModel);

            double maxDischarge = (int)(((coBat * 0.5) / 12D) * 100) / 100D;
            double maxCharge = (int)(((coBat * 0.5) / 12D) * 100) / 100D;

            battery.setMaxDischarge(maxDischarge);
            battery.setMaxCharge(maxCharge);
            battery.setInverter(mSystemSN);
            report("Storing battery");
            mToutcRepository.saveBatteryForScenario(assignedScenarioID, battery);
        }
    }

    @Override
    protected void generateBatterySchedules(String mSystemSN, String mFrom, String mTo, long assignedScenarioID) {
        // Do nothing, Not supported for Home Assistant
    }
}