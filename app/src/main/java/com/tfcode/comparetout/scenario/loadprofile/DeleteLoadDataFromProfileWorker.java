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
        if (mDeleteFirst) {
            mToutcRepository.deleteLoadProfileData(mLoadProfileID);
            mToutcRepository.deleteSimulationDataForProfileID(mLoadProfileID);
            mToutcRepository.deleteCostingDataForProfileID(mLoadProfileID);
        }
        return Result.success();
    }
}
