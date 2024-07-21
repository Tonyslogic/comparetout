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

package com.tfcode.comparetout.importers.alphaess;

import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.importers.ImportGenerateScenarioFragment;

import java.util.List;

public class ImportAlphaGenerateScenario extends ImportGenerateScenarioFragment {

    public ImportAlphaGenerateScenario() {
        mImporterType = ComparisonUIViewModel.Importer.ALPHAESS;
    }

    @Override
    protected void generateUsage() {
        Context context = getContext();
        if (null == context) return;

        String serializedPanelCounts = mStringPanelCount.stream()
                .map(Object::toString)
                .reduce("", (str, num) -> str.isEmpty() ? num : str + "," + num);
        Data inputData = new Data.Builder()
                .putString(GenerationWorker.KEY_SYSTEM_SN, mSystemSN)
                .putBoolean(GenerationWorker.LP, mLP)
                .putBoolean(GenerationWorker.INV, mINV)
                .putBoolean(GenerationWorker.PAN, mPNL)
                .putBoolean(GenerationWorker.PAN_D, mPNLD)
                .putBoolean(GenerationWorker.BAT, mBAT)
                .putBoolean(GenerationWorker.BAT_SCH, mBATS)
                .putString(GenerationWorker.FROM, mFrom)
                .putString(GenerationWorker.TO, mTo)
                .putInt(GenerationWorker.MPPT_COUNT, mMPPTCountValue)
                .putString(PANEL_COUNTS, serializedPanelCounts)
                .build();
        OneTimeWorkRequest generationWorkRequest =
                new OneTimeWorkRequest.Builder(GenerationWorker.class)
                        .setInputData(inputData)
                        .addTag(mSystemSN)
                        .build();

        WorkManager.getInstance(context).pruneWork();
        WorkManager
                .getInstance(context)
                .beginUniqueWork(mSystemSN, ExistingWorkPolicy.APPEND, generationWorkRequest)
                .enqueue();

        // set up the observer for the selected systems workers
        LiveData<List<WorkInfo>> mCatchupLiveDataForSN = WorkManager.getInstance(context)
                .getWorkInfosByTagLiveData(mSystemSN);
        Observer<List<WorkInfo>> mCatchupWorkObserver = workInfos -> {
            for (WorkInfo wi : workInfos) {
                String mFetchState;
                if ((!(null == wi)) &&
                        ((wi.getState() == WorkInfo.State.RUNNING) ||
                                (wi.getState() == WorkInfo.State.SUCCEEDED))) {
                    Data progress = wi.getProgress();
                    mFetchState = progress.getString(GenerationWorker.PROGRESS);
                    if (null == mFetchState) mFetchState = "Unknown state";
                    String finalMFetchState = mFetchState;
                    mMainHandler.post(() -> mGenStatus.setText(finalMFetchState));
                }
            }
        };
        mCatchupLiveDataForSN.observe((LifecycleOwner) context, mCatchupWorkObserver);
    }

    public static ImportAlphaGenerateScenario newInstance() {return new ImportAlphaGenerateScenario();}

}