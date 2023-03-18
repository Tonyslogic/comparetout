package com.tfcode.comparetout.scenario.loadprofile;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

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

public class GenerateLoadDataFromProfileWorker extends Worker {

    private final long mLoadProfileID;
    private final ToutcRepository mToutcRepository;
    private LoadProfile mLoadProfile;
    private final boolean mDeleteFirst;

    public GenerateLoadDataFromProfileWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mLoadProfileID = workerParams.getInputData().getLong("LoadProfileID", 0L);
        mDeleteFirst = workerParams.getInputData().getBoolean("DeleteFirst", false);
        mToutcRepository = new ToutcRepository((Application) context);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean needsDataGen = mToutcRepository.loadProfileDataCheck(mLoadProfileID);
        System.out.println("checkForDataAndGenerateIfNeeded ==> " + needsDataGen);

        if (mDeleteFirst) {
            System.out.println("Deleting load profile data for " + mLoadProfileID);
            mToutcRepository.deleteLoadProfileData(mLoadProfileID);
            System.out.println("Deleting simulation data for " + mLoadProfileID);
            mToutcRepository.deleteSimultationDataForProfileID(mLoadProfileID);
            System.out.println("Deleting costing data for " + mLoadProfileID);
            mToutcRepository.deleteCostingDataForProfileID(mLoadProfileID);
            // TODO Delete costings
            needsDataGen = true;
        }

        if (needsDataGen) {
            System.out.println("*********** WORKING on " + mLoadProfileID + " *******************");
            mLoadProfile = mToutcRepository.getLoadProfileWithLoadProfileID(mLoadProfileID);
            ArrayList<LoadProfileData> rows = new ArrayList<>();
            LocalDateTime active = LocalDateTime.of(2001, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2002,1,1, 0, 0);
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter minFormat = DateTimeFormatter.ofPattern("HH:mm");
            while (active.isBefore(end)) {
                LoadProfileData row = new LoadProfileData();
                row.setLoadProfileID(mLoadProfileID);
                row.setDate(active.format(dateFormat));
                row.setMinute(active.format(minFormat));
                row.setDow(active.getDayOfWeek().getValue());
                row.setMod(active.getHour() * 60  + active.getMinute());
                row.setLoad(genLoad(active.getMonth().getValue(),
                        active.getDayOfWeek().getValue(), active.getHour()));
                rows.add(row);
                active = active.plusMinutes(5);
            }
            System.out.println("adding " + rows.size() + " rows to DB for Load Profile: " + mLoadProfileID);
            mToutcRepository.createLoadProfileDataEntries(rows);
        }
        return Result.success();
    }

    private double genLoad(int month, int dow, int hod) {
        double load;
        int totalXXXDaysInMonth = countDayOccurrenceInMonth(DayOfWeek.of(dow), YearMonth.of(2010, month));

        double distMonth = mLoadProfile.getMonthlyDist().monthlyDist.get(month -1)/100;
        if (dow == 7) dow = 0; //Index 0 is used for Sun in the loadProfile...dowDist
        double distDOW = mLoadProfile.getDowDist().dowDist.get(dow)/100;
        double distHOD = mLoadProfile.getHourlyDist().dist.get(hod)/100;

        double monthuse = mLoadProfile.getAnnualUsage() * distMonth;
        double dayuse = (monthuse / totalXXXDaysInMonth) * distDOW;
        double houruse = dayuse * distHOD;

        load = houruse/12;

    return load;
    }

    public static int countDayOccurrenceInMonth(DayOfWeek dow, YearMonth month) {
        LocalDate start = month.atDay(1).with(TemporalAdjusters.nextOrSame(dow));
        return (int) ChronoUnit.WEEKS.between(start, month.atEndOfMonth()) + 1;
    }
}
