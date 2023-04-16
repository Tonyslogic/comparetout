package com.tfcode.comparetout.scenario;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import static java.lang.Double.max;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationWorker extends Worker {

    private final ToutcRepository mToutcRepository;

    public SimulationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatNeedSimulation();
        System.out.println("Found " + scenarioIDs.size() + " scenarios that need simulation");

        if (scenarioIDs.size() > 0) {
            // NOTIFICATION SETUP
            int notificationId = 1;
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
            builder.setContentTitle("Simulating scenarios")
                    .setContentText("Simulation in progress")
                    .setSmallIcon(R.drawable.housetick)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setTimeoutAfter(90000)
                    .setSilent(true);
            // Issue the initial notification with zero progress
            int PROGRESS_MAX = 100;
            int PROGRESS_CURRENT = 0;
            int PROGRESS_CHUNK = PROGRESS_MAX;
            if (scenarioIDs.size() > 0) {
                PROGRESS_CHUNK = PROGRESS_MAX / (scenarioIDs.size() + 1);
                builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                notificationManager.notify(notificationId, builder.build());
            }

            long startTime = System.nanoTime();
            for (long scenarioID : scenarioIDs) {
//                Scenario scenario = mToutcRepository.getScenarioForID(scenarioID);
                ScenarioComponents scenarioComponents = mToutcRepository.getScenarioComponentsForScenarioID(scenarioID);
                Scenario scenario = scenarioComponents.scenario;
                System.out.println("Working on scenario " + scenario.getScenarioName());
                if (scenario.isHasPanels()) {
                    // Check for panel data
                    boolean hasData = mToutcRepository.checkForMissingPanelData(scenarioID);// NOTIFICATION PROGRESS
                    System.out.println("SimulationWorker checkForMissingPanelData hasData = " + hasData);
                    PROGRESS_CURRENT += PROGRESS_CHUNK;
                    builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                    notificationManager.notify(notificationId, builder.build());
                    if (!hasData)
                    {
                        builder.setContentText("Skipping " + scenario.getScenarioName());
                        notificationManager.notify(notificationId, builder.build());PROGRESS_CURRENT += PROGRESS_CHUNK;
                        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                        notificationManager.notify(notificationId, builder.build());
                        continue;
                    }
                }
                builder.setContentText("Getting data: " + scenario.getScenarioName());
                notificationManager.notify(notificationId, builder.build());

                int rowsToProcess = 0;
                Map<Inverter, InputData> inputDataMap = new HashMap<>();

                // TODO: update DST if the simulation year or duration changes

                final int dstBegin = 25634;
                final int dstEnd = 25645;

                if (scenario.isHasInverters()) {

                    for (Inverter inverter: scenarioComponents.inverters) {
                        List<SimulationInputData> simulationInputData = mToutcRepository.getSimulationInputNoSolar(scenarioID);
                        rowsToProcess = simulationInputData.size();

                        List<Double> inverterPV = new ArrayList<>(Collections.nCopies(rowsToProcess, 0d));
                        for (int mppt = 1; mppt <= inverter.getMpptCount(); mppt++) {
                            List<Double> mpptPV = new ArrayList<>(Collections.nCopies(rowsToProcess, 0d));
                            for (Panel panel : scenarioComponents.panels) {
                                if (panel.getMppt() == mppt && panel.getInverter().equals(inverter.getInverterName())) {
                                    List<Double> panelPV = mToutcRepository.getSimulationInputForPanel(panel.getPanelIndex());
                                    if (panel.getConnectionMode() == Panel.PARALLEL)
                                    {
                                        for (int row = 0; row < panelPV.size(); row++)
                                            mpptPV.set(row, mpptPV.get(row) + panelPV.get(row));
                                    }
                                    else // Optimized
                                    {
                                        for (int row = 0; row < panelPV.size(); row++)
                                            mpptPV.set(row, max(mpptPV.get(row), panelPV.get(row)));
                                    }
                                }
                            }
                            for (int row = 0; row < mpptPV.size(); row++) {
                                inverterPV.set(row, inverterPV.get(row) + mpptPV.get(row));
                            }
                        }
                        // Populate the PV in the SimulationInputData and associate with inverter
//                        double totalPV = 0d;
                        for (int row = 0; row < rowsToProcess; row++) {
                            if (row < dstBegin)
                                simulationInputData.get(row).setTpv(inverterPV.get(row));
                            if (dstBegin <= row &&  row <= dstEnd)
                                simulationInputData.get(row).setTpv(0);
                            if (row > dstEnd) {
                                simulationInputData.get(row).setTpv(inverterPV.get(row - 12));
                            }
//                            totalPV += simulationInputData.get(row).getTpv();
                        }
                        inputDataMap.put(inverter, new InputData(inverter, simulationInputData));
//                        System.out.println("Calculated " + totalPV + " for inverter: " + inverter.getInverterName());
                    }
                }
                else {
                    Inverter inverter = new Inverter();
                    inverter.setInverterIndex(0);
                    inverter.setDc2acLoss(0);
                    inverter.setDc2dcLoss(0);
                    inverter.setAc2dcLoss(0);
                    inverter.setMinExcess(0);
                    InputData idata = new InputData(inverter, mToutcRepository.getSimulationInputNoSolar(scenarioID));
                    inputDataMap.put(inverter, idata);
                    rowsToProcess = idata.inputData.size();
                }

                builder.setContentText("Simulating: " + scenario.getScenarioName());
                notificationManager.notify(notificationId, builder.build());
                ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();

//                double dc2acLoss = new InputData(scenarioComponents.inverters.get(0)).dc2acLoss;

                for (int row = 0; row < rowsToProcess; row++) {
                    processOneRow(scenarioID, outputRows, row, inputDataMap);
                }
                System.out.println("adding " + outputRows.size() + " rows to DB for simulation: " + scenario.getScenarioName());

                builder.setContentText("Saving data");
                notificationManager.notify(notificationId, builder.build());

                mToutcRepository.saveSimulationDataForScenario(outputRows);

                // NOTIFICATION PROGRESS
                PROGRESS_CURRENT += PROGRESS_CHUNK;
                builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
                notificationManager.notify(notificationId, builder.build());
            }
            long endTime = System.nanoTime();
            System.out.println("Took " + (endTime - startTime) / 1000000 + "mS to simulate " + scenarioIDs.size() + " scenarios");

            if (scenarioIDs.size() > 0) {
                // NOTIFICATION COMPLETE
                builder.setContentText("Simulation complete")
                        .setProgress(0, 0, false);
                notificationManager.notify(notificationId, builder.build());
            }
        }
        return Result.success();
    }

    private void processOneRow(long scenarioID, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {

        Map.Entry<Inverter,InputData> entry = inputDataMap.entrySet().iterator().next();
        Inverter inverter = entry.getKey();
        InputData inputData = entry.getValue();
        SimulationInputData inputRow = inputData.inputData.get(row);

        double tPV = 0;
        double effectivePV = 0;
        for (Map.Entry<Inverter,InputData> etry: inputDataMap.entrySet()) {
            SimulationInputData sid = etry.getValue().inputData.get(row);
            tPV += sid.tpv;
            effectivePV += sid.tpv * etry.getValue().dc2acLoss;
        }

        ScenarioSimulationData outputRow = new ScenarioSimulationData();
        outputRow.setScenarioID(scenarioID);
        outputRow.setDate(inputRow.getDate());
        outputRow.setMinuteOfDay(inputRow.getMod());
        outputRow.setDayOfWeek(inputRow.getDow());
        outputRow.setDayOf2001((inputRow.getDo2001()));
        // TODO use the 'places electricity can come from and go to'
        outputRow.setLoad(inputRow.getLoad());
        outputRow.setPv(inputRow.getTpv());
        if (tPV > 0) {
            if (effectivePV >= inputRow.getLoad()) {
                outputRow.setFeed(effectivePV - inputRow.getLoad());
                outputRow.setBuy(0);
            }
            else {
                outputRow.setFeed(0);
                outputRow.setBuy(inputRow.getLoad() - effectivePV);
            }
        }
        else {
            outputRow.setFeed(0);
            outputRow.setBuy(inputRow.getLoad());
        }
        outputRow.setSOC(0);
        outputRow.setDirectEVcharge(0);
        outputRow.setWaterTemp(0);
        outputRow.setKWHDivToWater(0);
        outputRow.setKWHDivToEV(0);
        outputRow.setPvToCharge(0);
        outputRow.setPvToLoad(0);
        outputRow.setBatToLoad(0);
        outputRow.setImmersionLoad(0);
        outputRows.add(outputRow);
    }

    private static class InputData {
        double dc2acLoss;
        double ac2dcLoss;
        double dc2dcLoss;
        List<SimulationInputData> inputData;

        InputData(Inverter inverter, List<SimulationInputData> iData) {
            dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
            ac2dcLoss = (100d - inverter.getAc2dcLoss()) / 100d;
            dc2dcLoss = (100d - inverter.getDc2dcLoss()) / 100d;
            inputData = iData;
        }
    }
}
