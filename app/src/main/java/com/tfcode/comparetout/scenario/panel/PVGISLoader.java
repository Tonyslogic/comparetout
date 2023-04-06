package com.tfcode.comparetout.scenario.panel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.json.scenario.pgvis.Hourly;
import com.tfcode.comparetout.model.json.scenario.pgvis.PvGISData;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
    private final Context mContext;
    private final static Integer MAGIC_NUMBER = 919821;

    public PVGISLoader(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
        mContext = context;
    }

    private boolean fileExist(String filename){
        File file = mContext.getFileStreamPath(filename);
        return file.exists();
    }

    @NonNull
    @Override
    public Result doWork() {
        Long panelID = getInputData().getLong("panelID", 0);

        Panel mPanel = mToutcRepository.getPanelForID(panelID);
        DecimalFormat df = new DecimalFormat("#.000");
        String latitude = df.format(mPanel.getLatitude());
        String longitude = df.format(mPanel.getLongitude());
        String filename = "PVGIS(" + latitude + ")(" + longitude +
                ")(" + mPanel.getSlope() + ")(" + mPanel.getAzimuth() + ")" ;

        FileInputStream inputStream = null;
        try{
            if (!fileExist(filename)) Thread.sleep(1000);
            inputStream = mContext.openFileInput(filename);
            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type type = new TypeToken<PvGISData>(){}.getType();
            PvGISData pvGISData = new Gson().fromJson(reader, type);
            reader.close();

            ArrayList<PanelData> panelDataList = new ArrayList<>();
//            LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
//            LocalDateTime end = LocalDateTime.of(2002, 1, 1, 0, 0);
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter minFormat = DateTimeFormatter.ofPattern("HH:mm");

            DateTimeFormatter pvGisFormat = DateTimeFormatter.ofPattern("yyyyMMdd:HHmm");
            ZoneId localZone = ZoneOffset.systemDefault();

            for(Hourly pp : pvGISData.hourlies.hourlies){
                LocalDateTime utc = LocalDateTime.parse(pp.time, pvGisFormat);
                LocalDateTime active = utc.atZone(ZoneOffset.UTC).withZoneSameInstant(localZone).toLocalDateTime();
                active = active.minusHours(1);
                for (int i = 0; i < 12; i++) {
                    PanelData row = new PanelData();
                    row.setDo2001(active.getDayOfYear());
                    row.setPanelID(mPanel.getPanelIndex());
                    row.setDate(active.format(dateFormat));
                    row.setMinute(active.format(minFormat));
                    row.setDow(active.getDayOfWeek().getValue());
                    row.setMod(active.getHour() * 60 + active.getMinute());
                    row.setPv( (pp.gi / 12 / MAGIC_NUMBER) * mPanel.getPanelCount() * mPanel.getPanelkWp());
                    panelDataList.add(row);
                    active = active.plusMinutes(5);
                }
            }
            System.out.println("Got " + panelDataList.size() + " rows to store");
            mToutcRepository.savePanelData(panelDataList);

            System.out.println("Stored panelData in DB");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Result.retry();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return Result.failure();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Result.success();
    }
}
