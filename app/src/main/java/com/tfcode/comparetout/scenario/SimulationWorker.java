package com.tfcode.comparetout.scenario;

import static com.tfcode.comparetout.MainActivity.CHANNEL_ID;

import static java.lang.Double.max;
import static java.lang.Double.min;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
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

                // GET BASIC LOAD & PV FROM DB
                if (scenario.isHasInverters()) {
                    for (Inverter inverter: scenarioComponents.inverters) {
                        // Get some load simulation data to start with
                        List<SimulationInputData> simulationInputData = mToutcRepository.getSimulationInputNoSolar(scenarioID);
                        rowsToProcess = simulationInputData.size();

                        List<Double> inverterPV = new ArrayList<>(Collections.nCopies(rowsToProcess, 0d));
                        // get the total PV from all the panels related to the inverter
                        getPVForInverter(scenarioComponents, rowsToProcess, inverter, inverterPV);
                        // Populate the PV in the SimulationInputData and associate with inverter
                        mergePVWithSimulationInputData(rowsToProcess, dstBegin, dstEnd, simulationInputData, inverterPV);
                        // Get connected batteries (if any)
                        Battery connectedBattery = null;
                        if (scenario.isHasBatteries()) {
                            for (Battery battery: scenarioComponents.batteries)
                                if (battery.getInverter().equals(inverter.getInverterName()))
                                    connectedBattery = battery;
                        }
                        // Associate the inverter and the load for use in simulation
                        inputDataMap.put(inverter, new InputData(inverter, simulationInputData, connectedBattery));
                    }
                }
                else { // No solar simulation, but we need a 'perfect' inverter
                    Inverter inverter = new Inverter();
                    inverter.setInverterIndex(0);
                    inverter.setDc2acLoss(0);
                    inverter.setDc2dcLoss(0);
                    inverter.setAc2dcLoss(0);
                    inverter.setMinExcess(0);
                    InputData idata = new InputData(inverter, mToutcRepository.getSimulationInputNoSolar(scenarioID), null);
                    inputDataMap.put(inverter, idata);
                    rowsToProcess = idata.inputData.size();
                }

                // TODO: GET LOAD FROM CFG, CAR AND HOT WATER

                builder.setContentText("Simulating: " + scenario.getScenarioName());
                notificationManager.notify(notificationId, builder.build());

                // SIMULATE POWER DISTRIBUTION
                ArrayList<ScenarioSimulationData> outputRows = new ArrayList<>();
                for (int row = 0; row < rowsToProcess; row++) {
                    processOneRow(scenarioID, outputRows, row, inputDataMap);
                }

                // TODO: APPLY DIVERSION FOR EV AND HOT WATER

                // STORE THE SIMULATION RESULT
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

    private void mergePVWithSimulationInputData(int rowsToProcess, int dstBegin, int dstEnd, List<SimulationInputData> simulationInputData, List<Double> inverterPV) {
//        double totalPV = 0d;
        for (int row = 0; row < rowsToProcess; row++) {
            if (row < dstBegin)
                simulationInputData.get(row).setTpv(inverterPV.get(row));
            if (dstBegin <= row &&  row <= dstEnd)
                simulationInputData.get(row).setTpv(0);
            if (row > dstEnd) {
                simulationInputData.get(row).setTpv(inverterPV.get(row - 12));
            }
//            totalPV += simulationInputData.get(row).getTpv();
        }
//        System.out.println("Calculated " + totalPV + " for inverter: " + inverter.getInverterName());
    }

    private void getPVForInverter(ScenarioComponents scenarioComponents, int rowsToProcess, Inverter inverter, List<Double> inverterPV) {
        for (int mppt = 1; mppt <= inverter.getMpptCount(); mppt++) {
            List<Double> mpptPV = new ArrayList<>(Collections.nCopies(rowsToProcess, 0d));
            for (Panel panel : scenarioComponents.panels) {
                if (panel.getMppt() == mppt && panel.getInverter().equals(inverter.getInverterName())) {
                    List<Double> panelPV = mToutcRepository.getSimulationInputForPanel(panel.getPanelIndex());
                    if (panel.getConnectionMode() == Panel.PARALLEL)
                        for (int row = 0; row < panelPV.size(); row++)
                            mpptPV.set(row, mpptPV.get(row) + panelPV.get(row));
                    else // Optimized
                        for (int row = 0; row < panelPV.size(); row++)
                            mpptPV.set(row, max(mpptPV.get(row), panelPV.get(row)));
                }
            }
            for (int row = 0; row < mpptPV.size(); row++) {
                inverterPV.set(row, inverterPV.get(row) + mpptPV.get(row));
            }
        }
    }

    public static void processOneRow(long scenarioID, ArrayList<ScenarioSimulationData> outputRows, int row, Map<Inverter, InputData> inputDataMap) {

        Map.Entry<Inverter,InputData> entry = inputDataMap.entrySet().iterator().next();
        Inverter inverter = entry.getKey();
        InputData inputData = entry.getValue();
        SimulationInputData inputRow = inputData.inputData.get(row);

        // SETUP SOC AND BATTERY
        double previousOutputSOC;
        double totalBatteryCapacity = 0;
        double batteryAvailableForDischarge = 0;
        double batteryAvailableForCharge = 0;
        if (row > 0) {
            previousOutputSOC = outputRows.get(row - 1).getSOC();
            for (Map.Entry<Inverter,InputData> etry: inputDataMap.entrySet()) {
                Battery battery = etry.getValue().mBattery;
                double dischargeStop = (battery.getDischargeStop() / 100d) * battery.getBatterySize();
                totalBatteryCapacity += battery.getBatterySize();
                batteryAvailableForDischarge += min(battery.getMaxDischarge(),
                                max(0, (previousOutputSOC - dischargeStop )));
                batteryAvailableForCharge += min((battery.getBatterySize() - previousOutputSOC),
                        InputData.getMaxChargeForSOC(previousOutputSOC, battery));
            }
        }
        else { // Set initial SOC
            double totalBatteryReserve = 0;
            for (Map.Entry<Inverter,InputData> etry: inputDataMap.entrySet()) {
                Battery battery = etry.getValue().mBattery;
                totalBatteryCapacity += battery.getBatterySize();
                double batterySOC = (battery.getDischargeStop() / 100d) * battery.getBatterySize();
                etry.getValue().soc = batterySOC;
                totalBatteryReserve += batterySOC;
                double dischargeStop = (battery.getDischargeStop() / 100d) * battery.getBatterySize();
                batteryAvailableForDischarge += min(battery.getMaxDischarge(),
                        max(0, (batterySOC - dischargeStop )));
                batteryAvailableForCharge += min((battery.getBatterySize() - batterySOC),
                        InputData.getMaxChargeForSOC(batterySOC, battery));
            }
            previousOutputSOC = totalBatteryReserve;
        }

        // SETUP TOTAL AND EFFECTIVE PV
        double tPV = 0;
        double effectivePV = 0;
        for (Map.Entry<Inverter,InputData> etry: inputDataMap.entrySet()) {
            SimulationInputData sid = etry.getValue().inputData.get(row);
            tPV += sid.tpv;
            effectivePV += sid.tpv * etry.getValue().dc2acLoss;
        }

        double locallyAvailable = tPV + batteryAvailableForDischarge;
        boolean cfg = false; // TODO: configure this correctly
        if (cfg) locallyAvailable = tPV;

        ScenarioSimulationData outputRow = new ScenarioSimulationData();
        outputRow.setScenarioID(scenarioID);
        outputRow.setDate(inputRow.getDate());
        outputRow.setMinuteOfDay(inputRow.getMod());
        outputRow.setDayOfWeek(inputRow.getDow());
        outputRow.setDayOf2001((inputRow.getDo2001()));

        double inputLoad = inputRow.getLoad(); // TODO: Add scheduled loads
        outputRow.setLoad(inputLoad);
        outputRow.setPv(tPV);

        double buy = 0;
        double feed = 0;
        double pv2charge = 0;
        double pv2load = 0;
        double bat2Load = 0;
        double soc = 0;

        if (inputLoad > locallyAvailable) {
            buy = inputLoad - locallyAvailable;
            pv2load = tPV;
            if (!cfg) {
                soc = previousOutputSOC - batteryAvailableForDischarge * 1.01d; //TODO: Use battery::storageLoss
                bat2Load = batteryAvailableForDischarge * 1.01d; //TODO: Use battery::storageLoss
            }
        }
        else { // we cover the load without the grid
            if (inputLoad > tPV) {
                pv2load = tPV;
                if (!cfg) {
                    soc = previousOutputSOC - (inputLoad - tPV) * 1.01d; //TODO: Use battery::storageLoss
                    bat2Load = (inputLoad - tPV) * 1.01d; //TODO: Use battery::storageLoss
                }
                else buy = inputLoad - tPV;
            }
            else { // there is extra pv to charge/feed
                pv2load = inputLoad;
                if ((tPV - inputLoad) > inverter.getMinExcess()){
                    if (!cfg) {
                        double charge = min((tPV - inputLoad), batteryAvailableForCharge);
                        pv2charge = charge;
                        soc = previousOutputSOC + charge;
                        feed = tPV - inputLoad - charge;
                        if (inverter.getMaxInverterLoad() < (feed + charge))
                            feed = inverter.getMaxInverterLoad() - charge;
                    }
                    else {
                        // soc was already calculated
                        // but the feed does not consider this
                        feed = min((tPV - inputLoad), inverter.getMaxInverterLoad());
                    }
                    feed = max(0, feed * 0.95d); //TODO: FEED_MODIFIER
                }
            }
        }

        outputRow.setBuy(buy);
        outputRow.setFeed(feed);
        outputRow.setSOC(soc);
        outputRow.setPvToCharge(pv2charge);
        outputRow.setPvToLoad(pv2load);
        outputRow.setBatToLoad(bat2Load);

//        if (tPV > 0) {
//            if (effectivePV >= inputRow.getLoad()) {
//                outputRow.setFeed(effectivePV - inputRow.getLoad());
//                outputRow.setBuy(0);
//            }
//            else {
//                outputRow.setFeed(0);
//                outputRow.setBuy(inputRow.getLoad() - effectivePV);
//            }
//        }
//        else {
//            outputRow.setFeed(0);
//            outputRow.setBuy(inputRow.getLoad());
//        }
//        outputRow.setSOC(0);
//        outputRow.setPvToCharge(0);
//        outputRow.setPvToLoad(0);
//        outputRow.setBatToLoad(0);

        outputRow.setDirectEVcharge(0);
        outputRow.setWaterTemp(0);
        outputRow.setKWHDivToWater(0);
        outputRow.setKWHDivToEV(0);
        outputRow.setImmersionLoad(0);
        outputRows.add(outputRow);
    }

    static class InputData {
        double dc2acLoss;
        double ac2dcLoss;
        double dc2dcLoss;
        List<SimulationInputData> inputData;
        Battery mBattery;
        double soc = 0d;

        InputData(Inverter inverter, List<SimulationInputData> iData, Battery battery) {
            dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
            ac2dcLoss = (100d - inverter.getAc2dcLoss()) / 100d;
            dc2dcLoss = (100d - inverter.getDc2dcLoss()) / 100d;
            inputData = iData;
            mBattery = battery;
        }

        public static double getMaxChargeForSOC(double batterySOC, Battery battery) {
            double ret = 0;
            ChargeModel cm = battery.getChargeModel();
            double maxCharge = battery.getMaxCharge();
            double batteryPercentSOC = (batterySOC / battery.getBatterySize()) * 100d;
            if (batteryPercentSOC <= 12) ret = (maxCharge * cm.percent0) / 100d;
            if (batteryPercentSOC <= 90) ret = (maxCharge * cm.percent12) / 100d;
            if (batteryPercentSOC > 90) ret = (maxCharge * cm.percent90) / 100d;
            if (batteryPercentSOC == 100) ret = (maxCharge * cm.percent100) / 100d;
            return ret;
        }
    }
}
