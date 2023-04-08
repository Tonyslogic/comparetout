package com.tfcode.comparetout.scenario.panel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Panel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.text.DecimalFormat;

public class PVGISDownloader extends Worker {

    private final ToutcRepository mToutcRepository;
    private final Context mContext;
    private static final String U1 = "https://re.jrc.ec.europa.eu/api/v5_2/seriescalc?lat="; // LATITUDE
    private static final String U2 = "&lon="; // LONGITUDE
    private static final String U3 = "&raddatabase=PVGIS-SARAH2&browser=1&outputformat=json&userhorizon=&usehorizon=1&angle="; //SLOPE
    private static final String U4 = "&aspect="; // AZIMUTH
    private static final String U5 = "&startyear=2020&endyear=2020&mountingplace=&optimalinclination=0&optimalangles=0&js=1&select_database_hourly=PVGIS-SARAH2&hstartyear=2020&hendyear=2020&trackingtype=0&hourlyangle="; // SLOPE
    private static final String U6 = "&hourlyaspect="; //AZIMUTH
    // U1 <LAT> U2 <LONG> U3 <SLOPE> U4 <AZIMUTH> U5 <SLOPE> U6 <AZIMUTH>

    public PVGISDownloader(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mToutcRepository = new ToutcRepository((Application) context);
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
        System.out.println("Fetching " + filename);

        if (!fileExist(filename)) new Thread(() -> {
            URL url;

            try {
                // U1 <LAT> U2 <LONG> U3 <SLOPE> U4 <AZIMUTH> U5 <SLOPE> U6 <AZIMUTH>
                int az = mPanel.getAzimuth();
                if (az > 180) az = 360 - az;
                StringBuilder spec = new StringBuilder()
                        .append(U1).append(df.format(mPanel.getLatitude()))
                        .append(U2).append(df.format(mPanel.getLongitude()))
                        .append(U3).append(mPanel.getSlope())
                        .append(U4).append(az)
                        .append(U5).append(mPanel.getSlope())
                        .append(U6).append(az);

                url = new URL(spec.toString());
                InputStreamReader reader = new InputStreamReader(url.openStream());
                BufferedReader bufferedReader = new BufferedReader(reader);
                StringBuilder pvData = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    pvData.append(line).append("\n");
                }

                try (OutputStreamWriter outputStreamWriter =
                             new OutputStreamWriter(this.getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE))) {
                    outputStreamWriter.write(pvData.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        else {
            System.out.println("File found already -- TODO LOAD");
        }
        return Result.success();
    }
}
