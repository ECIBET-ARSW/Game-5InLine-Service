package com.ecibet.game5inline.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WebSocketSessionManager {

    private final Map<String, String> sessionToLobby = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionToWebSocket = new ConcurrentHashMap<>();
    private final Map<String, String> userToSession = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String userId, String lobbyCode) {
        sessionToLobby.put(sessionId, lobbyCode);
        sessionToUser.put(sessionId, userId);
        userToSession.put(userId, sessionId);
    }

    public void setUserSession(String userId, WebSocketSession session) {
        userToSession.put(userId, session.getId());
        sessionToWebSocket.put(session.getId(), session);
    }

    public void unregisterSession(String sessionId) {
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            userToSession.remove(userId);
        }
        sessionToLobby.remove(sessionId);
        sessionToWebSocket.remove(sessionId);
    }

    public String getLobbyBySession(String sessionId) {
        return sessionToLobby.get(sessionId);
    }

    public String getUserIdBySession(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessionToWebSocket.get(sessionId);
    }

    public Set<String> getSessionsByLobby(String lobbyCode) {
        return sessionToLobby.entrySet().stream()
                .filter(entry -> lobbyCode.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}