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

import com.tfcode.comparetout.importers.homeassistant.HADispatcher;

public class AuthOKHandler implements MessageHandler<AuthRequired>{

    private final HADispatcher dispatcher;

    public AuthOKHandler(HADispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
    @Override
    public void handleMessage(HAMessage message) {
        AuthOK authRequired = (AuthOK) message;
        dispatcher.setAuthorized(true);
    }

    @Override
    public Class<? extends HAMessage> getMessageClass() {
        return AuthOK.class;
    }
}
