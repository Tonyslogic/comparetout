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

package com.tfcode.comparetout.importers.homeassistant.messages.authorization;

import com.tfcode.comparetout.importers.homeassistant.HADispatcher;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;
import com.tfcode.comparetout.importers.homeassistant.MessageHandler;

public class AuthInvalidHandler implements MessageHandler<AuthRequired> {

    private final HADispatcher dispatcher;

    public AuthInvalidHandler(HADispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
    @Override
    public void handleMessage(HAMessage message) {
        AuthInvalid authInvalid = (AuthInvalid) message;
        dispatcher.setAuthorized(false);
    }

    @Override
    public Class<? extends HAMessage> getMessageClass() {
        return AuthInvalid.class;
    }
}
