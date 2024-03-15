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

package com.tfcode.comparetout.importers.homeassistant;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthInvalidHandler;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthOKHandler;
import com.tfcode.comparetout.importers.homeassistant.messages.authorization.AuthRequiredHandler;
import com.tfcode.comparetout.importers.homeassistant.messages.HAMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class HADispatcher {
    private final OkHttpClient client;
    private final Gson gson;
    private final Map<String, MessageHandler<? extends HAMessage>> handlers;
    private final Map<Integer, MessageHandler<? extends HAMessage>> pendingRequests;
    private WebSocket webSocket;
    private final AtomicInteger idGenerator;
    private boolean authorized = false;

    private final String url;

    private static final Logger LOGGER = Logger.getLogger(HADispatcher.class.getName());

    /**
     * Create a new Home Assistant WebSocket API
     * <p>
     *     This constructor creates a new Home Assistant WebSocket API with the given URL and authentication token.
     *     Three handlers are registered for the "auth_required", "auth_invalid", and "auth_ok" message types.
     *     On connection the server will send an "auth_required" message. These handlers will handle the authentication process.
     * </p>
     *
     * @param url        The URL of the WebSocket API
     * @param auth_token The authentication token for the WebSocket API
     */
    public HADispatcher(String url, String auth_token) {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.handlers = new HashMap<>();
        this.pendingRequests = new HashMap<>();
        this.idGenerator = new AtomicInteger(0);
        this.url = url;

        registerHandler("auth_required", new AuthRequiredHandler(this, auth_token));
        registerHandler("auth_invalid", new AuthInvalidHandler(this));
        registerHandler("auth_ok", new AuthOKHandler(this));
        LOGGER.info("HADispatcher initialized");
    }

    public void  setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    /**
     * Register a handler for a message type
     * <p>
     *     This method registers a handler for a message type.
     *     The handler will be called when a message of the given type is received.
     *     The handler will be called with the message as an argument.
     * </p>
     *
     * @param type    The message type
     * @param handler The message handler
     * @param <T>     The type of the message
     */
    public <T extends HAMessage> void registerHandler(String type, MessageHandler<T> handler) {
        handlers.put(type, handler);
    }

    /**
     * Start the Home Assistant WebSocket API
     * <p>
     *     This method starts the Home Assistant WebSocket API.
     *     The WebSocket API is started with the given URL.
     *     The WebSocket API is started with the given client.
     *     The WebSocket API is started with a new WebSocket listener.
     *     The WebSocket listener listens for messages from the WebSocket API.
     *     The WebSocket listener passes the messages to the handlers.
     *     The WebSocket listener passes the messages to the pending requests.
     *     The WebSocket listener passes the messages to the handlers based on the message type.
     *     The WebSocket listener passes the messages to the pending requests based on the message ID.
     * </p>
     */
    public void start() {
        LOGGER.info("Starting HADispatcher");
        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                LOGGER.info("Received message: " + text);

                JsonElement jsonElement = gson.fromJson(text, JsonElement.class);
                String type = jsonElement.getAsJsonObject().get("type").getAsString();
                LOGGER.info("Getting handler for type: " + type);
                if ("result".equals(type)) {
                    int id = jsonElement.getAsJsonObject().get("id").getAsInt();
                    MessageHandler<? extends HAMessage> handler = pendingRequests.get(id);
                    if (handler != null) {
                        Class<? extends HAMessage> messageClass = handler.getMessageClass();
                        LOGGER.info("Got handler: " + messageClass.getName() + " for id: " + id);
                        HAMessage message = gson.fromJson(text, messageClass);
                        handler.handleMessage(message);
                    }
                }
                else {
                    MessageHandler<? extends HAMessage> handler = handlers.get(type);
                    if (handler != null) {
                        Class<? extends HAMessage> messageClass = handler.getMessageClass();
                        LOGGER.info("Got handler: " + messageClass.getName());
                        HAMessage message = gson.fromJson(text, messageClass);
                        handler.handleMessage(message);
                    }
                }
            }
        });
        LOGGER.info("HADispatcher started");
    }

    /**
     * Stop the Home Assistant WebSocket API
     * <p>
     *     This method stops the Home Assistant WebSocket API.
     *     The WebSocket API is stopped with a normal closure.
     *     The WebSocket API is stopped with a status code of 1000.
     *     The WebSocket API is stopped with a reason of "Normal closure".
     * </p>
     */
    public void stop() {
        webSocket.close(1000, "Normal closure");
    }

    /**
     * Send a message to the Home Assistant WebSocket API
     * <p>
     *     This method sends a message to the Home Assistant WebSocket API and registers a handler for the result message.
     *     The result message will be passed to the handler when it is received.
     *     The handler will be removed after the result message is received.
     *     If the result message is not received, the handler will not be removed.
     *     The message is sent as a JSON string.
     *     The message ID is used to match the result message to the request message.
     *     The message ID is also used to remove the handler after the result message is received.
     *     The message ID is unique for each message.
     *     The message ID is generated by the client.
     *     The message ID is an integer.
     *     The message ID is included in the result message.
     *     The resultMessage may be null. If the resultMessage is null, the handler will not be added to the pending requests.
     *     A null resultMessage is used for messages that do not have an id (authorization, etc.).
     * </p>
     *
     * @param message       The message to send
     * @param resultMessage The handler for the result message
     * @param <T>           The type of the result message
     */
    public <T extends HAMessage> void sendMessage(HAMessage message, MessageHandler<T> resultMessage) {
        if (!authorized && !"auth".equals(message.getType())) {
            throw new IllegalStateException("Not authorized");
        }
        String messageJson = gson.toJson(message);
        LOGGER.info("Sending message: " + messageJson);
        webSocket.send(messageJson);
        if (!(null == resultMessage)) pendingRequests.put(message.getId(), resultMessage);
    }

    /**
     * Generate a unique ID
     * <p>
     *     This method generates a unique ID.
     *     The ID is generated by incrementing an AtomicInteger.
     *     The AtomicInteger is thread-safe.
     *     The AtomicInteger is initialized with 0.
     *     The AtomicInteger is incremented each time a new ID is generated.
     *     The ID is unique for each message.
     * </p>
     *
     * @return The unique ID
     */
    public int generateId() {
        return idGenerator.incrementAndGet();
    }
}
