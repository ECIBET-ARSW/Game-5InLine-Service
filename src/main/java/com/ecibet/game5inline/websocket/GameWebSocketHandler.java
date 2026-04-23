package com.ecibet.game5inline.websocket;

import com.ecibet.game5inline.cache.ActiveLobbiesCache;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.enums.LobbyStatus;
import com.ecibet.game5inline.service.GameEngine;
import com.ecibet.game5inline.service.GameEngineManager;
import com.ecibet.game5inline.service.LobbyManager;
import com.ecibet.game5inline.websocket.dto.inbound.*;
import com.ecibet.game5inline.websocket.dto.outbound.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;
    private final ActiveLobbiesCache lobbyCache;
    private final LobbyManager lobbyManager;
    private final GameEngineManager gameEngineManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> json = objectMapper.readValue(payload, Map.class);
        String type = (String) json.get("type");

        switch (type) {
            case "PLAYER_ACTION":
                handlePlayerAction(session, json);
                break;
            case "CHANGE_COLOR":
                handleChangeColor(session, json);
                break;
            case "TOGGLE_READY":
                handleToggleReady(session, json);
                break;
            case "START_GAME":
                handleStartGame(session, json);
                break;
            case "LEAVE_ROOM":
                handleLeaveRoom(session, json);
                break;
            default:
                log.warn("Unknown message type: {}", type);
        }
    }

    private void handlePlayerAction(WebSocketSession session, Map<String, Object> json) throws Exception {
        String userId = sessionManager.getUserIdBySession(session.getId());
        if (userId == null) return;

        String lobbyCode = sessionManager.getLobbyBySession(session.getId());
        if (lobbyCode == null) return;

        Lobby lobby = lobbyCache.getByCode(lobbyCode).orElse(null);
        if (lobby == null || lobby.getStatus() != LobbyStatus.IN_PROGRESS) return;

        GameEngine engine = gameEngineManager.getEngine(lobby.getId());
        if (engine == null) return;

        String action = (String) json.get("action");
        Long timestamp = json.get("timestamp") != null ? ((Number) json.get("timestamp")).longValue() : System.currentTimeMillis();

        engine.addInput(userId, action, timestamp);
    }

    private void handleChangeColor(WebSocketSession session, Map<String, Object> json) {
        String userId = sessionManager.getUserIdBySession(session.getId());
        if (userId == null) return;

        String lobbyCode = sessionManager.getLobbyBySession(session.getId());
        if (lobbyCode == null) return;

        Lobby lobby = lobbyCache.getByCode(lobbyCode).orElse(null);
        if (lobby == null || lobby.getStatus() != LobbyStatus.WAITING) return;

        String colorStr = (String) json.get("color");
        // Color change logic would go here
    }

    private void handleToggleReady(WebSocketSession session, Map<String, Object> json) {
        String userId = sessionManager.getUserIdBySession(session.getId());
        if (userId == null) return;

        String lobbyCode = sessionManager.getLobbyBySession(session.getId());
        if (lobbyCode == null) return;

        Lobby lobby = lobbyCache.getByCode(lobbyCode).orElse(null);
        if (lobby == null || lobby.getStatus() != LobbyStatus.WAITING) return;

        Player player = lobby.getPlayers().get(userId);
        if (player != null) {
            Boolean isReady = json.get("isReady") != null ? (Boolean) json.get("isReady") : !player.getIsReady();
            player.setIsReady(isReady);
            lobbyCache.put(lobby);

            broadcastToLobby(lobbyCode, PlayerReadyChangedMessage.from(userId, isReady));
        }
    }

    private void handleStartGame(WebSocketSession session, Map<String, Object> json) {
        String userId = sessionManager.getUserIdBySession(session.getId());
        if (userId == null) return;

        String lobbyCode = (String) json.get("lobbyCode");
        if (lobbyCode == null) return;

        try {
            lobbyManager.startGame(lobbyCode, userId);
        } catch (Exception e) {
            log.error("Failed to start game: {}", e.getMessage());
        }
    }

    private void handleLeaveRoom(WebSocketSession session, Map<String, Object> json) {
        String userId = sessionManager.getUserIdBySession(session.getId());
        if (userId == null) return;

        String lobbyCode = (String) json.get("lobbyCode");
        if (lobbyCode == null) return;

        try {
            lobbyManager.leaveLobby(lobbyCode, userId);
            sessionManager.unregisterSession(session.getId());
        } catch (Exception e) {
            log.error("Failed to leave room: {}", e.getMessage());
        }
    }

    private void broadcastToLobby(String lobbyCode, Object message) {
        // Broadcast implementation would go here
        // This would iterate through sessions in the lobby and send the message
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = sessionManager.getUserIdBySession(session.getId());
        if (userId != null) {
            String lobbyCode = sessionManager.getLobbyBySession(session.getId());
            if (lobbyCode != null) {
                try {
                    lobbyManager.leaveLobby(lobbyCode, userId);
                } catch (Exception e) {
                    log.error("Failed to leave lobby on disconnect: {}", e.getMessage());
                }
            }
        }
        sessionManager.unregisterSession(session.getId());
        log.info("WebSocket connection closed: {}", session.getId());
    }
}