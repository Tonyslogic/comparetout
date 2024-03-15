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

import java.util.logging.Logger;

public class AuthRequiredHandler implements MessageHandler<AuthRequired> {

    private final HADispatcher dispatcher;
    private final String token;

    private static final Logger LOGGER = Logger.getLogger(AuthRequiredHandler.class.getName());


    public AuthRequiredHandler(HADispatcher dispatcher, String token) {
        this.dispatcher = dispatcher;
        this.token = token;
    }

    @Override
    public void handleMessage(HAMessage message) {
        LOGGER.info("AuthRequiredHandler.handleMessage");
        AuthRequired authRequired = (AuthRequired) message;
        String version = authRequired.getHaVersion();
        // TODO: check if the version is supported

        Auth auth = new Auth();
        auth.setAccessToken(token);
        dispatcher.sendMessage(auth, null);
    }

    @Override
    public Class<? extends HAMessage> getMessageClass() {
        return AuthRequired.class;
    }
}
