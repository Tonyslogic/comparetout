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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules the wizard's save state rests on, pulled out of the ViewModel so they
 * can be tested without one.
 *
 * Both had been patched repeatedly in place. The name rule lived in the composition,
 * so `save()` could not consult it and learned about a duplicate only by watching the
 * INSERT fail; the panel-id rule did not exist at all, which is why a second save
 * deleted and re-created panels that had only just been written.
 */
class WizardSaveStateTest {

    private val existing = listOf(1L to "Home", 2L to "Home with battery")

    // ── name rule ─────────────────────────────────────────────────────────────

    @Test
    fun aFreeNameIsAccepted() {
        assertNull(wizardNameProblem("Something else", -1L, existing))
    }

    @Test
    fun aTakenNameIsRejectedForAnUnsavedWizard() {
        assertEquals(WizardNameProblem.IN_USE, wizardNameProblem("Home", -1L, existing))
    }

    /** The whole point of adopting the id after a save: a scenario may keep its name. */
    @Test
    fun aScenarioKeepsItsOwnName() {
        assertNull(wizardNameProblem("Home", 1L, existing))
        assertEquals("but not someone else's",
            WizardNameProblem.IN_USE, wizardNameProblem("Home", 2L, existing))
    }

    /**
     * Blank used to pass, because only Run required a name. Save then wrote a scenario
     * called "" and the second one collided on the UNIQUE index — reported to the user
     * as a duplicate name for two scenarios they had never named.
     */
    @Test
    fun blankIsAProblemOfItsOwn() {
        assertEquals(WizardNameProblem.BLANK, wizardNameProblem("", -1L, existing))
        assertEquals(WizardNameProblem.BLANK, wizardNameProblem("   ", -1L, existing))
    }

    // ── panel ids / dirty tracking ────────────────────────────────────────────

    private fun builderWithPanels(vararg names: String) = WizardBuilder(
        scenarioName = "xyz",
        panelEntries = names.map { WizardPanelEntry(panelName = it) }
    )

    @Test
    fun savingStampsTheAssignedRowIdsOntoTheEntries() {
        val before = builderWithPanels("Roof south", "Roof east")
        val assigned = mapOf(
            before.panelEntries[0].id to 41L,
            before.panelEntries[1].id to 42L
        )

        val after = before.withPanelIds(assigned)

        assertEquals(listOf(41L, 42L), after.panelEntries.map { it.panelIndex })
        assertEquals("entry identity survives — only the row id changes",
            before.panelEntries.map { it.id }, after.panelEntries.map { it.id })
    }

    @Test
    fun anEntryWithNoAssignedIdIsLeftAlone() {
        val before = builderWithPanels("Roof south", "Roof east")
        val after = before.withPanelIds(mapOf(before.panelEntries[0].id to 41L))

        assertEquals(41L, after.panelEntries[0].panelIndex)
        assertEquals(0L, after.panelEntries[1].panelIndex)
    }

    @Test
    fun anEmptyAssignmentChangesNothing() {
        val before = builderWithPanels("Roof south")
        assertEquals(before, before.withPanelIds(emptyMap()))
    }

    /**
     * isDirty is `snapshot != null && builder != snapshot`. The baseline recorded at
     * save time must therefore be the id-stamped builder: baselining the pre-save one
     * would leave the wizard permanently dirty, since the live builder now carries the
     * assigned ids.
     */
    @Test
    fun theSavedBaselineMatchesTheStampedBuilder() {
        val written = builderWithPanels("Roof south")
        val assigned = mapOf(written.panelEntries[0].id to 41L)

        val live = written.withPanelIds(assigned)
        val baseline = written.withPanelIds(assigned)

        assertFalse("stamping is not a no-op", written == live)
        assertTrue("saving leaves the wizard clean", live == baseline)
    }

    /** An edit made while the save was in flight must survive as unsaved. */
    @Test
    fun anEditDuringTheSaveStaysDirty() {
        val written = builderWithPanels("Roof south")
        val assigned = mapOf(written.panelEntries[0].id to 41L)
        val baseline = written.withPanelIds(assigned)

        val editedMidSave = written.withPanelIds(assigned).copy(annualUsage = "9999")

        assertFalse(editedMidSave == baseline)
    }
}
