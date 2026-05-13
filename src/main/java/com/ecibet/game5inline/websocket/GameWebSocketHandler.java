package com.ecibet.game5inline.websocket;

import com.ecibet.game5inline.cache.ActiveLobbiesCache;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.enums.LobbyStatus;
import com.ecibet.game5inline.model.enums.PlayerColor;
import com.ecibet.game5inline.service.GameEngine;
import com.ecibet.game5inline.service.GameEngineManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;
    private final ActiveLobbiesCache lobbyCache;
    private final GameEngineManager gameEngineManager;

    private final Map<String, Long> lastToggleTime = new ConcurrentHashMap<>();

    @EventListener
    public void handleGameEvent(GameEvent event) {
        String eventType = event.getType();
        String lobbyCode = event.getLobbyCode();
        Object data = event.getData();

        log.info("=== GAME EVENT RECEIVED === Type: {}, Lobby: {}", eventType, lobbyCode);

        if (lobbyCode == null) {
            log.warn("Lobby code is null, ignoring event");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", eventType);

        if (data instanceof Map) {
            message.putAll((Map<String, Object>) data);
        }

        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
            log.info("Broadcasting to lobby {}: {}", lobbyCode, jsonMessage);
        } catch (Exception e) {
            log.error("Error serializing: {}", e.getMessage());
            return;
        }

        Set<String> sessionIds = sessionManager.getSessionsByLobby(lobbyCode);
        log.info("Found {} sessions for lobby {}", sessionIds.size(), lobbyCode);

        int sentCount = 0;
        for (String sessionId : sessionIds) {
            WebSocketSession session = sessionManager.getSession(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(jsonMessage));
                    }
                    sentCount++;
                    log.debug("Message sent to session {}", sessionId);
                } catch (Exception e) {
                    log.error("Error sending to session {}: {}", sessionId, e.getMessage());
                }
            }
        }
        log.info("Event {} sent to {} sessions in lobby {}", eventType, sentCount, lobbyCode);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("Message received: {}", payload);

        String userId = sessionManager.getUserIdBySession(session.getId());
        String lobbyCode = sessionManager.getLobbyBySession(session.getId());

        try {
            Map<String, Object> json = objectMapper.readValue(payload, Map.class);
            String type = (String) json.get("type");
            if (type == null) return;

            log.info("Processing message type: {} for user: {}, lobby: {}", type, userId, lobbyCode);

            if ("INIT".equals(type)) {
                handleInit(session, json);
                return;
            }

            if (userId == null || lobbyCode == null) {
                log.warn("User {} or lobby {} not found for session {}", userId, lobbyCode, session.getId());
                if ("PING".equals(type)) {
                    sendPong(session);
                }
                return;
            }

            Lobby lobby = lobbyCache.getByCode(lobbyCode).orElse(null);
            if (lobby == null) {
                log.warn("Lobby {} not found", lobbyCode);
                return;
            }

            switch (type) {
                case "PLAYER_ACTION":
                    handlePlayerAction(session, json, userId, lobbyCode, lobby);
                    break;
                case "TOGGLE_READY":
                    handleToggleReady(session, json, userId, lobbyCode, lobby);
                    break;
                case "START_GAME":
                    handleStartGame(session, json, userId, lobbyCode, lobby);
                    break;
                case "LEAVE_ROOM":
                    handleLeaveRoom(session, json, userId, lobbyCode, lobby);
                    break;
                case "CHANGE_COLOR":
                    handleChangeColor(session, json, userId, lobbyCode, lobby);
                    break;
                case "CLIENT_READY":
                    handleClientReady(session, userId, lobbyCode, lobby);
                    break;
                case "PING":
                    sendPong(session);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
        }
    }

    private void handleInit(WebSocketSession session, Map<String, Object> json) {
        String userId = (String) json.get("userId");
        String lobbyCode = (String) json.get("lobbyCode");

        log.info("HANDLE INIT - userId: {}, lobbyCode: {}", userId, lobbyCode);

        if (userId != null && lobbyCode != null) {
            sessionManager.registerSession(session.getId(), userId, lobbyCode);
            sessionManager.setUserSession(userId, session);
            log.info("User {} registered in lobby {} via SockJS INIT", userId, lobbyCode);
            broadcastLobbyUpdate(lobbyCode);

            try {
                Map<String, Object> response = new HashMap<>();
                response.put("type", "INIT_ACK");
                response.put("status", "ok");
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                log.info("INIT_ACK sent to session {}", session.getId());
            } catch (Exception e) {
                log.error("Error sending INIT_ACK: {}", e.getMessage());
            }
        } else {
            log.warn("INIT message missing userId or lobbyCode: {}", json);
        }
    }

    private void sendPong(WebSocketSession session) {
        try {
            Map<String, Object> pong = new HashMap<>();
            pong.put("type", "PONG");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            log.debug("PONG sent to session {}", session.getId());
        } catch (Exception e) {
            log.error("Error sending pong: {}", e.getMessage());
        }
    }

    private void handlePlayerAction(WebSocketSession session, Map<String, Object> json, String userId, String lobbyCode, Lobby lobby) {
        if (lobby.getStatus() != LobbyStatus.IN_PROGRESS) return;
        GameEngine engine = gameEngineManager.getEngine(lobby.getId());
        if (engine == null) return;
        String action = (String) json.get("action");
        Long timestamp = json.get("timestamp") != null ? ((Number) json.get("timestamp")).longValue() : System.currentTimeMillis();
        engine.addInput(userId, action, timestamp);
        log.debug("Player action - User: {}, Action: {}", userId, action);
    }

    private void handleToggleReady(WebSocketSession session, Map<String, Object> json, String userId, String lobbyCode, Lobby lobby) {
        long now = System.currentTimeMillis();
        Long lastTime = lastToggleTime.get(userId);
        if (lastTime != null && (now - lastTime) < 1000) return;
        lastToggleTime.put(userId, now);

        Player player = lobby.getPlayers().get(userId);
        if (player != null) {
            player.setIsReady(!player.getIsReady());
            lobbyCache.put(lobby);
            log.info("Player {} toggled ready to: {}", userId, player.getIsReady());
            broadcastLobbyUpdate(lobbyCode);
            broadcastLobbyUpdate(lobbyCode);
        }
    }

    private void handleStartGame(WebSocketSession session, Map<String, Object> json, String userId, String lobbyCode, Lobby lobby) {
        log.info("START_GAME request from user {} in lobby {}", userId, lobbyCode);

        if (!lobby.getHostId().equals(userId)) {
            log.warn("User {} is not host", userId);
            return;
        }

        if (lobby.getPlayers().size() < lobby.getMinPlayers()) {
            log.warn("Not enough players: {}/{}", lobby.getPlayers().size(), lobby.getMinPlayers());
            return;
        }

        if (!lobby.allPlayersReady()) {
            log.warn("Not all players ready");
            return;
        }

        lobby.setStatus(LobbyStatus.IN_PROGRESS);
        lobbyCache.put(lobby);

        log.info("Starting game engine for lobby {}", lobbyCode);
        gameEngineManager.startEngine(lobby, lobby.getGameConfig());
    }

    private void handleLeaveRoom(WebSocketSession session, Map<String, Object> json, String userId, String lobbyCode, Lobby lobby) {
        lobby.removePlayer(userId);
        if (lobby.getPlayers().isEmpty()) {
            lobbyCache.remove(lobby.getId());
        } else {
            lobbyCache.put(lobby);
            broadcastLobbyUpdate(lobbyCode);
        }
        sessionManager.unregisterSession(session.getId());
        log.info("Player {} left lobby {}", userId, lobbyCode);
    }

    private void handleChangeColor(WebSocketSession session, Map<String, Object> json, String userId, String lobbyCode, Lobby lobby) {
        String color = (String) json.get("color");
        Player player = lobby.getPlayers().get(userId);
        if (player != null) {
            try {
                player.setColor(PlayerColor.valueOf(color.toUpperCase()));
                lobbyCache.put(lobby);
                broadcastLobbyUpdate(lobbyCode);
                log.info("Player {} changed color to {}", userId, color);
            } catch (IllegalArgumentException e) {
                log.error("Invalid color: {}", color);
            }
        }
    }

    private void handleClientReady(WebSocketSession session, String userId, String lobbyCode, Lobby lobby) {
        GameEngine engine = gameEngineManager.getEngine(lobby.getId());
        if (engine != null) {
            engine.addReadyClient(userId);
            log.info("Client {} is ready for game in lobby {}", userId, lobbyCode);
        } else {
            log.warn("No game engine found for lobby {}", lobbyCode);
        }
    }

    public void broadcastLobbyUpdate(String lobbyCode) {
        try {
            Lobby lobby = lobbyCache.getByCode(lobbyCode).orElse(null);
            if (lobby == null) {
                log.warn("Lobby {} not found for broadcast", lobbyCode);
                return;
            }

            List<Map<String, Object>> playersList = new ArrayList<>();
            for (Player player : lobby.getPlayers().values()) {
                Map<String, Object> playerMap = new HashMap<>();
                playerMap.put("userId", player.getUserId());
                playerMap.put("username", player.getUsername());
                playerMap.put("color", player.getColor().name().toLowerCase());
                playerMap.put("isReady", player.getIsReady());
                playerMap.put("isHost", player.getUserId().equals(lobby.getHostId()));
                playersList.add(playerMap);
            }

            Map<String, Object> message = new HashMap<>();
            message.put("type", "LOBBY_UPDATE");
            message.put("players", playersList);
            message.put("lobbyCode", lobbyCode);
            message.put("betAmount", lobby.getBetAmount());
            message.put("minPlayers", lobby.getMinPlayers());
            message.put("currentPlayers", lobby.getPlayers().size());
            message.put("maxPlayers", lobby.getMaxPlayers());
            message.put("hostId", lobby.getHostId());

            String jsonMessage = objectMapper.writeValueAsString(message);
            log.info("Broadcasting LOBBY_UPDATE to lobby {}: {}", lobbyCode, jsonMessage);

            int sentCount = 0;
            for (String sessionId : sessionManager.getSessionsByLobby(lobbyCode)) {
                WebSocketSession session = sessionManager.getSession(sessionId);
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage(jsonMessage));
                    sentCount++;
                }
            }
            log.info("LOBBY_UPDATE sent to {} sessions", sentCount);
        } catch (Exception e) {
            log.error("Error broadcasting lobby update: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = sessionManager.getUserIdBySession(session.getId());
        String lobbyCode = sessionManager.getLobbyBySession(session.getId());

        log.info("WebSocket connection closed - userId: {}, lobbyCode: {}", userId, lobbyCode);

        if (userId != null && lobbyCode != null) {
            Lobby lobby = lobbyCache.getByCode(lobbyCode).orElse(null);
            if (lobby != null) {
                lobby.removePlayer(userId);
                if (!lobby.getPlayers().isEmpty()) {
                    lobbyCache.put(lobby);
                    broadcastLobbyUpdate(lobbyCode);
                } else {
                    lobbyCache.remove(lobby.getId());
                }
            }
        }
        sessionManager.unregisterSession(session.getId());
    }
}