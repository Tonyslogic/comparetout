/*
 * Copyright (c) 2023. Tony Finnerty
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

package com.tfcode.comparetout.importers.alphaess;

import android.annotation.SuppressLint;

import com.tfcode.comparetout.importers.alphaess.responses.GetOneDayPowerResponse;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class DataMassager {

    public static Map<Long, FiveMinuteEnergies> massage(Map<Long, FiveMinuteEnergies> fixed, double ePV, double eLoad, double eFeed) {
        Map<Long, FiveMinuteEnergies> massaged = new TreeMap<>();
        double pPVTotal = 0;
        double pLoadTotal = 0;
        double pFeedTotal = 0;
        for (Map.Entry<Long, FiveMinuteEnergies> entry : fixed.entrySet()) {
            pPVTotal += entry.getValue().pv ;
            pLoadTotal += entry.getValue().load;
            pFeedTotal += entry.getValue().feed;
        }
        // fixed values are W
        // we treat them as 5 minute samples for kWh
        // / 1000d ==> kW, / 12d ==> kWh
        pPVTotal = (pPVTotal / 1000d) / 12d;
        pLoadTotal = (pLoadTotal / 1000d) / 12d;
        pFeedTotal = (pFeedTotal / 1000d) / 12d;
        // Unitize and scale to total load
        for (Map.Entry<Long, FiveMinuteEnergies> entry : fixed.entrySet()) {
            double mPV = ((entry.getValue().pv / 1000d) / 12d) / pPVTotal * ePV;
            double mLoad = ((entry.getValue().load / 1000d) / 12d) / pLoadTotal * eLoad;
            double mFeed = ((entry.getValue().feed / 1000d) / 12d) / pFeedTotal * eFeed;
            massaged.put(entry.getKey(), new FiveMinuteEnergies(mPV, mLoad, mFeed));
        }
        // Handle fallback DST
        if (massaged.size() == 289) {
            System.out.println("DST day detected");
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            Set<String> check = new HashSet<>();
            Map<String, Long> lookup = new HashMap<>();
            Long keyToUpdate = 0L;
            Long keyToRemove = 0L;
            for (Map.Entry<Long, FiveMinuteEnergies> row : massaged.entrySet()) {
                String hhmm = sdf.format(new Date(row.getKey()));
                boolean ck = check.add(hhmm);
                if (!ck) {
                    keyToRemove = row.getKey();
                    keyToUpdate = lookup.get(hhmm);
                    break;
                }
                else lookup.put(hhmm, row.getKey());
            }
            FiveMinuteEnergies pairToUpdate = massaged.get(keyToUpdate);
            FiveMinuteEnergies pairToRemove = massaged.get(keyToRemove);
            Double updatedPV = (Double) (pairToUpdate != null ? pairToUpdate.pv : 0.0) +
                    (Double) (pairToRemove != null ? pairToRemove.pv : 0.0);
            Double updatedLoad = (Double) (pairToUpdate != null ? pairToUpdate.load : 0.0) +
                    (Double) (pairToRemove != null ? pairToRemove.load : 0.0);
            Double updatedFeed = (Double) (pairToUpdate != null ? pairToUpdate.feed : 0.0) +
                    (Double) (pairToRemove != null ? pairToRemove.feed : 0.0);
            massaged.put(keyToUpdate, new FiveMinuteEnergies(updatedPV, updatedLoad, updatedFeed));
            massaged.remove(keyToRemove);
        }
        return massaged;
    }

    static class DataPoint {
        double ppv;
        double load;
        double feed;
        long timestamp; // Assuming timestamps are in milliseconds

        public DataPoint(double ppv, double load, double feed, long timestamp) {
            this.ppv = ppv;
            this.load = load;
            this.feed = feed;
            this.timestamp = timestamp;
        }
    }

    public static List<DataPoint> getDataPointsForPowerResponse(GetOneDayPowerResponse oneDayPowerResponse) {
        List<DataPoint> dataPointList = new ArrayList<>();
        if (!(null == oneDayPowerResponse) && !(null == oneDayPowerResponse.data)) {
            for (GetOneDayPowerResponse.DataItem item : oneDayPowerResponse.data) {
                // Get uploadTime and get milliseconds for it
                long uplTime = 0;
                @SuppressLint("SimpleDateFormat") SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                try {
                    Date date = dateFormat.parse(item.uploadTime);
                    if (!(null == date)) uplTime = date.getTime();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                double ppv = item.ppv;
                double load = item.load;
                double feed = item.feedIn;
                DataPoint dp = new DataPoint(ppv, load, feed, uplTime);
                dataPointList.add(dp);
            }
        }
        return dataPointList;
    }

    public static Map<Long, FiveMinuteEnergies> oneDayDataInFiveMinuteIntervals(List<DataPoint> inputData) {
        Map<Long, List<Double>> ppvIntervalMap = new TreeMap<>();
        Map<Long, FiveMinuteEnergies> averagedData = new TreeMap<>();
        Map<Long, List<Double>> loadIntervalMap = new TreeMap<>();
        Map<Long, List<Double>> feedIntervalMap = new TreeMap<>();

        int intervalSize = 5 * 60 * 1000; // Interval size in milliseconds (5 minutes)

        long midnightHour = 0L;
        long fiveBefore = 0L;

        // Group data points into intervals based on timestamp
        for (DataPoint dataPoint : inputData) {
            if (midnightHour == 0) {
                LocalDateTime date =
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(dataPoint.timestamp), ZoneId.systemDefault());
                LocalDateTime midnight = LocalDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), 0, 0);
                ZonedDateTime zdt = midnight.atZone(ZoneId.systemDefault());
                midnightHour = zdt.toInstant().toEpochMilli();
                LocalDateTime fiveTo = LocalDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), 23, 55);
                zdt = fiveTo.atZone(ZoneId.systemDefault());
                fiveBefore = zdt.toInstant().toEpochMilli();

            }
            long intervalKey = dataPoint.timestamp / intervalSize * intervalSize;

            ppvIntervalMap.computeIfAbsent(intervalKey, k -> new ArrayList<>()).add(dataPoint.ppv);
            loadIntervalMap.computeIfAbsent(intervalKey, k -> new ArrayList<>()).add(dataPoint.load);
            feedIntervalMap.computeIfAbsent(intervalKey, k -> new ArrayList<>()).add(dataPoint.feed);
        }
        if (null == ppvIntervalMap.get(midnightHour)) {
            // Looks like the first entry arrived after 00:05
            System.out.println("Adding midnight");
            ppvIntervalMap.computeIfAbsent(midnightHour, k -> new ArrayList<>()).add(0D);
            loadIntervalMap.computeIfAbsent(midnightHour, k -> new ArrayList<>()).add(0D);
            feedIntervalMap.computeIfAbsent(midnightHour, k -> new ArrayList<>()).add(0D);
        }
        if (null == ppvIntervalMap.get(fiveBefore)) {
            // Looks like the first entry arrived after 00:05
            System.out.println("Adding last entry, Five before tomorrow");
            ppvIntervalMap.computeIfAbsent(fiveBefore, k -> new ArrayList<>()).add(0D);
            loadIntervalMap.computeIfAbsent(fiveBefore, k -> new ArrayList<>()).add(0D);
            feedIntervalMap.computeIfAbsent(fiveBefore, k -> new ArrayList<>()).add(0D);
        }

        // Calculate averages for each interval
        for (Map.Entry<Long, List<Double>> entry : ppvIntervalMap.entrySet()) {
            long intervalStart = entry.getKey();
            List<Double> pvValues = entry.getValue();
            List<Double> loadValues = loadIntervalMap.get(intervalStart);
            List<Double> feedValues = feedIntervalMap.get(intervalStart);

            double pvSum = 0.0;
            for (double value : pvValues) pvSum += value;
            double pvAverage = pvSum / pvValues.size();

            double loadSum = 0.0;
            double loadAverage = 0.0;
            if (loadValues != null) {
                for (double value : loadValues) loadSum += value;
                loadAverage = loadSum / loadValues.size();
            }


            double feedSum = 0.0;
            double feedAverage = 0.0;
            if (feedValues != null) {
                for (double value : feedValues) feedSum += value;
                feedAverage = feedSum / feedValues.size();
            }

            averagedData.put(intervalStart, new FiveMinuteEnergies(pvAverage, loadAverage, feedAverage));
        }

        // Find missing intervals and add linear interpretation
        averagedData = interpolateMissingEntries(averagedData);

        return averagedData;
    }

    private static Map<Long, FiveMinuteEnergies> interpolateMissingEntries(Map<Long, FiveMinuteEnergies> data) {
        Map<Long, FiveMinuteEnergies> interpolatedData = new TreeMap<>();
        Long previousKey = null;
        Double previousPV = null;
        Double previousLoad = null;
        Double previousFeed = null;

        for (Map.Entry<Long, FiveMinuteEnergies> entry : data.entrySet()) {
            if (previousKey != null && previousKey + 1 != entry.getKey()) {
                interpolate(previousKey, entry.getKey(), previousPV, previousLoad, previousFeed, entry.getValue(), interpolatedData);
            }
            interpolatedData.put(entry.getKey(), entry.getValue());
            previousKey = entry.getKey();
            previousPV = entry.getValue().pv;
            previousLoad = entry.getValue().load;
            previousFeed = entry.getValue().feed;
        }
        return interpolatedData;
    }

    private static void interpolate(long startKey, long endKey,
                        double startPV, double startLoad, double startFeed,
                        FiveMinuteEnergies endValue, Map<Long, FiveMinuteEnergies> data) {
        long interval = endKey - startKey;
        double pvInterval = endValue.pv - startPV;
        double loadInterval = endValue.load - startLoad;
        double feedInterval = endValue.feed - startFeed;
        if (startKey + 5 * 60 * 1000 > endKey) {
            for (long i = startKey + 5 * 60 * 1000; i < endKey; i += 5 * 60 * 1000) {
                double fraction = (i - startKey) / (double)interval;
                double interpolatedPV = startPV + (fraction * pvInterval);
                double interpolatedLoad = startLoad + (fraction * loadInterval);
                double interpolatedFeed = startFeed + (fraction * feedInterval);
                data.put(i, new FiveMinuteEnergies(interpolatedPV, interpolatedLoad, interpolatedFeed));
            }
        }
        else {
            double interpolatedPV = (startPV + endValue.pv) / 2;
            double interpolatedLoad = (startLoad + endValue.load) / 2;
            double interpolatedFeed = (startFeed + endValue.feed) / 2;
            data.put(startKey + 5 * 60 * 1000, new FiveMinuteEnergies(interpolatedPV, interpolatedLoad, interpolatedFeed));
        }
    }
}