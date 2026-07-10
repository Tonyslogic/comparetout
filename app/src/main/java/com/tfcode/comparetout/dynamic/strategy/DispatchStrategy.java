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

package com.tfcode.comparetout.dynamic.strategy;

/**
 * An offline scenario-authoring strategy: given one day's context (prices,
 * load estimate, battery state), decide that day's charge/discharge windows.
 * The {@link StrategyYearRunner} walks the year day by day and the
 * {@link ScheduleEmitter} turns the decisions into LoadShift /
 * DischargeToGrid rows for a generated scenario.
 *
 * <p>Not to be confused with the simulation engine's own DispatchStrategy
 * (the per-interval bus-priority enum) — this one runs at authoring time,
 * not inside the simulator.
 */
public interface DispatchStrategy {

    /** Short human-readable name; becomes part of the generated scenario's name. */
    String name();

    /** Decide one day's dispatch windows. */
    DayDecisions decideDay(DayContext ctx);
}
