/*
 * Copyright (c) 2026. Tony Finnerty
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

package com.tfcode.comparetout.ui2

import com.tfcode.comparetout.model.scenario.Battery
import com.tfcode.comparetout.model.scenario.HeatPump
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COPY/IMPORT saves go through [WizardBuilder.toScenarioComponents], which is an
 * INSERT-only path: every component row is created fresh.
 *
 * A builder populated from an existing scenario carries the SOURCE row's primary
 * key on batteries and heat pumps. addNewScenarioWithComponents inserts those with
 * a plain @Insert (ABORT), so a carried-over id collided on the primary key and the
 * resulting SQLiteConstraintException was reported to the user as "the name is
 * already in use" — with the whole scenario rolled back.
 */
class WizardCopyComponentIdsTest {

    private fun sourceBattery(index: Long) = Battery().also {
        it.batteryIndex = index
        it.batterySize = 5.0
        it.inverter = "AlphaESS"
    }

    private fun sourceHeatPump(index: Long) = HeatPump().also {
        it.heatPumpIndex = index
        it.fuelType = "Kerosene/Oil"
    }

    private fun copyBuilder() = WizardBuilder(
        scenarioName = "renamed copy",
        scenarioMode = ScenarioMode.COPY,
        basedOnId = 3L,
        batteryEntries = listOf(sourceBattery(42L).toWizardBatteryEntry()),
        heatPumpEntries = listOf(sourceHeatPump(7L).toWizardHeatPumpEntry())
    )

    @Test
    fun copiedComponentsInsertAsNewRows() {
        val components = copyBuilder().toScenarioComponents()

        assertEquals(1, components.batteries.size)
        assertEquals(0L, components.batteries[0].batteryIndex)
        assertEquals(1, components.heatPumps.size)
        assertEquals(0L, components.heatPumps[0].heatPumpIndex)
    }

    @Test
    fun loadLinkedShellAlsoInsertsNewRows() {
        val components = copyBuilder().toScenarioShellWithEV()

        assertEquals(0L, components.batteries[0].batteryIndex)
        assertEquals(0L, components.heatPumps[0].heatPumpIndex)
    }

    /** The entry still remembers where it came from — only the DB-bound row is reset. */
    @Test
    fun builderEntriesKeepTheirSourceIds() {
        val b = copyBuilder()

        assertEquals(42L, b.batteryEntries[0].batteryIndex)
        assertEquals(7L, b.heatPumpEntries[0].heatPumpIndex)
    }
}
