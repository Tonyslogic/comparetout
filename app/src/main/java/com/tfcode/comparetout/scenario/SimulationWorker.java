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
    private static final Battery M_NULL_BATTERY = new Battery();
    static {
        M_NULL_BATTERY.setBatterySize(0);
        M_NULL_BATTERY.setDischargeStop(100);
        M_NULL_BATTERY.setMaxDischarge(0);
        M_NULL_BATTERY.setMaxCharge(0);
        M_NULL_BATTERY.setInverter("");
        M_NULL_BATTERY.setStorageLoss(0);
    }

    public SimulationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mToutcRepository = new ToutcRepository((Application) context);

    }

    @Override
    public void onStopped(){
        System.out.println("SimulationWorker::onStopped");
        super.onStopped();
    }

    @NonNull
    @Override
    public Result doWork() {
        List<Long> scenarioIDs = mToutcRepository.getAllScenariosThatNeedSimulation();
        System.out.println("Found " + scenarioIDs.size() + " scenarios that need simulation");

        try {
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
                        if (!hasData) {
                            builder.setContentText("Skipping " + scenario.getScenarioName());
                            notificationManager.notify(notificationId, builder.build());
                            PROGRESS_CURRENT += PROGRESS_CHUNK;
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
                        for (Inverter inverter : scenarioComponents.inverters) {
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
                                for (Battery battery : scenarioComponents.batteries)
                                    if (battery.getInverter().equals(inverter.getInverterName()))
                                        connectedBattery = battery;
                            }
                            // Associate the inverter and the load for use in simulation
                            inputDataMap.put(inverter, new InputData(inverter, simulationInputData, connectedBattery));
                        }
                    } else { // No solar simulation, but we need a 'perfect' inverter
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
        }
        catch (Exception e) {
            System.out.println("!!!!!!!!!!!!!!!!!!! SimulationWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            e.printStackTrace();
            System.out.println("!!!!!!!!!!!!!!!!!!! SimulationWorker has crashed, marking as failure !!!!!!!!!!!!!!!!!!!!!");
            return Result.failure();
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
                InputData iData = etry.getValue();
                if (null == iData.mBattery) {
                    previousOutputSOC = 0;
                    iData.soc = 0;
                    iData.mBattery = M_NULL_BATTERY;
                }
                totalBatteryCapacity += iData.mBattery.getBatterySize();
                batteryAvailableForDischarge += iData.getDischargeCapacity();
                batteryAvailableForCharge += iData.getChargeCapacity();
//                Battery battery = etry.getValue().mBattery;
//                if (null == battery) {
//                    previousOutputSOC = 0;
//                    battery = M_NULL_BATTERY;
//                }
//                double dischargeStop = (battery.getDischargeStop() / 100d) * battery.getBatterySize();
//                totalBatteryCapacity += battery.getBatterySize();
//                batteryAvailableForDischarge += min(battery.getMaxDischarge(),
//                                max(0, (previousOutputSOC - dischargeStop )));
//                batteryAvailableForCharge += min((battery.getBatterySize() - previousOutputSOC),
//                        InputData.getMaxChargeForSOC(previousOutputSOC, battery));
            }
        }
        else { // Set initial SOC
            double totalBatteryReserve = 0;
            for (Map.Entry<Inverter,InputData> etry: inputDataMap.entrySet()) {
                InputData iData = etry.getValue();
                if (null == iData.mBattery) {
                    iData.soc = 0;
                    iData.mBattery = M_NULL_BATTERY;
                }
                totalBatteryCapacity += iData.mBattery.getBatterySize();
                double batterySOC = iData.getDischargeStop();
                iData.soc = batterySOC;
                totalBatteryReserve += batterySOC;
                batteryAvailableForDischarge += iData.getDischargeCapacity();
                batteryAvailableForCharge += iData.getChargeCapacity();

//                Battery battery = etry.getValue().mBattery;
//                if (null == battery) battery = M_NULL_BATTERY;
//                totalBatteryCapacity += battery.getBatterySize();
//                double batterySOC = (battery.getDischargeStop() / 100d) * battery.getBatterySize();
//                etry.getValue().soc = batterySOC;
//                totalBatteryReserve += batterySOC;
//                double dischargeStop = (battery.getDischargeStop() / 100d) * battery.getBatterySize();
//                batteryAvailableForDischarge += min(battery.getMaxDischarge(),
//                        max(0, (batterySOC - dischargeStop )));
//                batteryAvailableForCharge += min((battery.getBatterySize() - batterySOC),
//                        InputData.getMaxChargeForSOC(batterySOC, battery));
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

        // COPY THE BASICS TO THE OUTPUT
        ScenarioSimulationData outputRow = new ScenarioSimulationData();
        outputRow.setScenarioID(scenarioID);
        outputRow.setDate(inputRow.getDate());
        outputRow.setMinuteOfDay(inputRow.getMod());
        outputRow.setDayOfWeek(inputRow.getDow());
        outputRow.setDayOf2001((inputRow.getDo2001()));

        double inputLoad = inputRow.getLoad(); // TODO: Add scheduled loads
        outputRow.setLoad(inputLoad);
        outputRow.setPv(tPV);

        // SIMULATE WHERE STUFF GOES

        double buy = 0;
        double feed = 0;
        double pv2charge = 0;
        double pv2load = 0;
        double bat2Load = 0;
        double totalSOC = 0;

        if (inputLoad > locallyAvailable) {
            buy = inputLoad - locallyAvailable;
            pv2load = tPV;
            if (!cfg) {
                totalSOC = previousOutputSOC - batteryAvailableForDischarge * 1.01d; //TODO: Use battery::storageLoss
                dischargeBatteries(inputDataMap, batteryAvailableForDischarge);
                bat2Load = batteryAvailableForDischarge * 1.01d; //TODO: Use battery::storageLoss
            }
        }
        else { // we cover the load without the grid
            if (inputLoad > tPV) {
                pv2load = tPV;
                if (!cfg) {
                    totalSOC = previousOutputSOC - (inputLoad - tPV) * 1.01d; //TODO: Use battery::storageLoss
                    dischargeBatteries(inputDataMap, (inputLoad - tPV));
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
                        totalSOC = previousOutputSOC + charge;
                        chargeBatteries(inputDataMap, charge);
                        feed = tPV - inputLoad - charge;
                        if (inverter.getMaxInverterLoad() < (feed + charge))
                            feed = inverter.getMaxInverterLoad() - charge;
                    }
                    else {
                        // totalSOC was already calculated
                        // but the feed does not consider this
                        feed = min((tPV - inputLoad), inverter.getMaxInverterLoad());
                    }
                    feed = max(0, feed * 0.95d); //TODO: FEED_MODIFIER
                }
            }
        }

        outputRow.setBuy(buy);
        outputRow.setFeed(feed);
        outputRow.setSOC(totalSOC);
        outputRow.setPvToCharge(pv2charge);
        outputRow.setPvToLoad(pv2load);
        outputRow.setBatToLoad(bat2Load);


        // DIVERSIONS & SCHEDULES
        outputRow.setDirectEVcharge(0);
        outputRow.setWaterTemp(0);
        outputRow.setKWHDivToWater(0);
        outputRow.setKWHDivToEV(0);
        outputRow.setImmersionLoad(0);

        // RECORD THE OUTPUT
        outputRows.add(outputRow);
    }

    private static void chargeBatteries(Map<Inverter, InputData> inputDataMap, double charge) {
        Map.Entry<Inverter,InputData> entry = inputDataMap.entrySet().iterator().next();
        entry.getValue().soc += charge; // TODO use dc2dcLoss
    }

    private static void dischargeBatteries(Map<Inverter, InputData> inputDataMap, double discharge) {
        Map.Entry<Inverter,InputData> entry = inputDataMap.entrySet().iterator().next();
        entry.getValue().soc -= discharge * entry.getValue().storageLoss;
    }

    static class InputData {
        long id;
        double dc2acLoss;
        double ac2dcLoss;
        double dc2dcLoss;
        double storageLoss;
        List<SimulationInputData> inputData;
        Battery mBattery;
        double soc = 0d;

        InputData(Inverter inverter, List<SimulationInputData> iData, Battery battery) {
            id = inverter.getInverterIndex();
            dc2acLoss = (100d - inverter.getDc2acLoss()) / 100d;
            ac2dcLoss = (100d - inverter.getAc2dcLoss()) / 100d;
            dc2dcLoss = (100d - inverter.getDc2dcLoss()) / 100d;
            storageLoss = (null == battery) ? 0 : battery.getStorageLoss();
            inputData = iData;
            mBattery = battery;
        }

        public double getDischargeStop() {
            return (mBattery.getDischargeStop() / 100d) * mBattery.getBatterySize();
        }

        public double getChargeCapacity() {
            return min((mBattery.getBatterySize() - soc),
                    InputData.getMaxChargeForSOC(soc, mBattery));
        }

        public double getDischargeCapacity() {
            return min(mBattery.getMaxDischarge(),
                    max(0, (soc - getDischargeStop() )));
        }

        public static double getMaxChargeForSOC(double batterySOC, Battery battery) {
            double ret = 0;
            if (null == battery) return ret;
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
