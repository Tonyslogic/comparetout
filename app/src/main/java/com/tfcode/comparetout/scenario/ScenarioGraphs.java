/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.scenario;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.ImportGraphsFragment;

public class ScenarioGraphs extends ImportGraphsFragment {

    public ScenarioGraphs() {
        mImporterType = ComparisonUIViewModel.Importer.SIMULATION;
    }

    public static ScenarioGraphs newInstance() {
        return new ScenarioGraphs();
    }

    @Override
    protected void setupPopupFilterMenu() {
        super.setupPopupFilterMenu();
        if (!(null == mFilterPopup)) {
            mFilterPopup.getMenu().findItem(R.id.pv2bat).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.pv2load).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.bat2load).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.gridToBattery).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.evSchedule).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.hwSchedule).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.evDivert).setVisible(true);
            mFilterPopup.getMenu().findItem(R.id.hwDivert).setVisible(true);
        }
    }
}