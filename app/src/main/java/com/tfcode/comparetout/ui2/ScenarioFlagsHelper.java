package com.tfcode.comparetout.ui2;

import androidx.annotation.Nullable;
import com.tfcode.comparetout.model.scenario.Scenario;

/** Exposes Scenario boolean flags whose names collide with their private backing fields,
 *  preventing direct Kotlin 2.x property access. */
public final class ScenarioFlagsHelper {
    private ScenarioFlagsHelper() {}

    public static boolean hasIRData(@Nullable Scenario scenario) {
        return scenario != null && scenario.isHasIRData();
    }

    public static boolean hasHWDivert(@Nullable Scenario scenario) {
        return scenario != null && scenario.isHasHWDivert();
    }
}
