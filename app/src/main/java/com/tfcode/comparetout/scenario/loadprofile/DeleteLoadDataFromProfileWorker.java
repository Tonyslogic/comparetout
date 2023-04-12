package com.tfcode.comparetout.scenario.loadprofile;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tfcode.comparetout.model.ToutcRepository;

public class DeleteLoadDataFromProfileWorker extends Worker {

    private final long mLoadProfileID;
    private final ToutcRepository mToutcRepository;
    private final boolean mDeleteFirst;

    public DeleteLoadDataFromProfileWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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
            mToutcRepository.deleteSimulationDataForProfileID(mLoadProfileID);
            System.out.println("Deleting costing data for " + mLoadProfileID);
            mToutcRepository.deleteCostingDataForProfileID(mLoadProfileID);
        }
        return Result.success();
    }
}
