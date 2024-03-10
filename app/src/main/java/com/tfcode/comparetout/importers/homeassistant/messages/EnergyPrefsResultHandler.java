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

package com.tfcode.comparetout.importers.homeassistant.messages;

import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsResult.EnergyPrefsResult;
import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsResult.EnergySource;
import com.tfcode.comparetout.importers.homeassistant.messages.EnergyPrefsResult.Flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EnergyPrefsResultHandler implements MessageHandler<EnergyPrefsResult>{

    private static final Logger LOGGER = Logger.getLogger(EnergyPrefsResultHandler.class.getName());

    private String statSolarEnergyFrom;
    private String statBatteryEnergyFrom;
    private String statBatteryEnergyTo;
    private List<String> statGridEnergyFrom;
    private List<String> statGridEnergyTo;

    public List<String> getSensors() {
        List<String> ret = new ArrayList<>();
        ret.add(statSolarEnergyFrom);
        ret.add(statBatteryEnergyFrom);
        ret.add(statBatteryEnergyTo);
        ret.addAll(statGridEnergyFrom);
        ret.addAll(statGridEnergyTo);
        return ret;
    }

    @Override
    public void handleMessage(HAMessage message) {
        LOGGER.info("EnergyPrefsResultHandler.handleMessage");
        EnergyPrefsResult result = (EnergyPrefsResult) message;
        if (result.isSuccess()) {
            // TODO: This is a result message, so we need to do something with it

            Optional<EnergySource> solarEnergySource = result.getResult().getEnergySources().stream()
                    .filter(energySource -> "solar".equals(energySource.getType()))
                    .findFirst();

            if (solarEnergySource.isPresent()) {
                statSolarEnergyFrom = solarEnergySource.get().getStatEnergyFrom();
                LOGGER.info("solar, flow_from = " + statSolarEnergyFrom);
            } else {
                LOGGER.info("solar, flow_from = DOH! Think again" );
            }

            Optional<EnergySource> batteryEnergySource = result.getResult().getEnergySources().stream()
                    .filter(energySource -> "battery".equals(energySource.getType()))
                    .findFirst();

            if (batteryEnergySource.isPresent()) {
                statBatteryEnergyFrom = batteryEnergySource.get().getStatEnergyFrom();
                LOGGER.info("battery, flow_from = " + statBatteryEnergyFrom);
                statBatteryEnergyTo = batteryEnergySource.get().getStatEnergyTo();
                LOGGER.info("battery, flow_to = " + statBatteryEnergyTo);
            } else {
                LOGGER.info("solar, flow_from = DOH! Think again" );
            }

            Optional<EnergySource> gridEnergySource = result.getResult().getEnergySources().stream()
                    .filter(energySource -> "grid".equals(energySource.getType()))
                    .findFirst();

            if (gridEnergySource.isPresent()) {
                statGridEnergyFrom = gridEnergySource.get().getFlowFrom().stream().map(Flow::getStatEnergyFrom).collect(Collectors.toList());
                statGridEnergyTo = gridEnergySource.get().getFlowTo().stream().map(Flow::getStatEnergyTo).collect(Collectors.toList());
                LOGGER.info("grid, flow_from = " + String.join(", ", statGridEnergyFrom));
                LOGGER.info("grid, flow_to = " + String.join(", ", statGridEnergyTo));
            } else {
                LOGGER.info("grid, flow_from = DOH! Think again" );
            }
        }
    }

    @Override
    public Class<? extends HAMessage> getMessageClass() {
        return EnergyPrefsResult.class;
    }
}
