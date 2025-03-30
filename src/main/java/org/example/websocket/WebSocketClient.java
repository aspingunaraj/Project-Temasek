package org.example.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.websocket.listeners.OnCloseListener;
import org.example.websocket.listeners.OnErrorListener;
import org.example.websocket.listeners.OnMessageListener;
import org.example.websocket.listeners.OnOpenListener;
import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.PreferenceDto;
import org.example.websocket.model.Tick;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

@ClientEndpoint
public class WebSocketClient {

    private Session session;
    private final String accessToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Listener interfaces
    private OnOpenListener onOpenListener;
    private OnCloseListener onCloseListener;
    private OnErrorListener onErrorListener;
    private OnMessageListener onMessageListener;
    private WebSocketParser webSocketParser = new WebSocketParser();

    public WebSocketClient(String accessToken) {
        this.accessToken = accessToken;
    }

    // Establish connection to the WebSocket server using JWT token
    public void connect() {
        String url = "wss://developer-ws.paytmmoney.com/broadcast/user/v1/data?x_jwt_token=" + accessToken;
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(url));
        } catch (Exception e) {
            if (onErrorListener != null) onErrorListener.onError(e);
        }
    }

    // Triggered when the WebSocket is successfully opened
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        if (onOpenListener != null) onOpenListener.onOpen();
    }

    // Triggered when WebSocket connection is closed
    @OnClose
    public void onClose(Session session, CloseReason reason) {
        if (onCloseListener != null) onCloseListener.onClose(reason.getReasonPhrase());
    }

    // Triggered when an error occurs on the WebSocket
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("🔴 WebSocket error: " + throwable.getMessage());
        throwable.printStackTrace();

        if (onErrorListener != null) {
            onErrorListener.onError(new RuntimeException(throwable.getMessage()));
        }
    }

    // Triggered when a binary message is received from WebSocket
    @OnMessage
    public void onMessage(ByteBuffer buffer) {

        List<Tick> ticks = webSocketParser.parse(buffer); // Parse binary stream into Tick DTOs
        if (onMessageListener != null && !ticks.isEmpty()) {
            onMessageListener.onMessage(new ArrayList<>(ticks));
        }
    }

    /**
     * Parses the incoming binary message buffer into Tick DTOs.
     * Handles LTP, QUOTE, FULL packet types and their Index variants.
     */


    /**
     * Send subscription preferences to WebSocket.
     * Call this after connection is established.
     */
    public void subscribe(List<PreferenceDto> preferences) {
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(preferences);
                session.getAsyncRemote().sendText(json);
            } catch (JsonProcessingException e) {
                if (onErrorListener != null) onErrorListener.onError(e);
            }
        }
    }

    // Close the WebSocket connection manually
    public void closeConnection() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                if (onErrorListener != null) onErrorListener.onError(e);
            }
        }
    }

    // Setters for listener callbacks
    public void setOnOpenListener(OnOpenListener listener) {
        this.onOpenListener = listener;
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.onCloseListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        this.onErrorListener = listener;
    }

    public void setOnMessageListener(OnMessageListener listener) {
        this.onMessageListener = listener;
    }
}
