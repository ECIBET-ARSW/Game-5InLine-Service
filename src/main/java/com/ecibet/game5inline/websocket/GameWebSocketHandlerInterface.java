package com.ecibet.game5inline.websocket;

import java.util.Map;
import org.springframework.web.socket.WebSocketHandler;

public interface GameWebSocketHandlerInterface extends WebSocketHandler {
    void handleGameEvent(Map<String, Object> event);
    void broadcastLobbyUpdate(String lobbyCode);
}