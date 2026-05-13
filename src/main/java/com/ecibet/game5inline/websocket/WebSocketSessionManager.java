package com.ecibet.game5inline.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToLobbyMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> lobbySessionsMap = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> userSessionMap = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String userId, String lobbyCode) {
        sessionToUserMap.put(sessionId, userId);
        sessionToLobbyMap.put(sessionId, lobbyCode);

        lobbySessionsMap.computeIfAbsent(lobbyCode, k -> new CopyOnWriteArraySet<>()).add(sessionId);

        log.info("Session {} registered - userId: {}, lobbyCode: {}", sessionId, userId, lobbyCode);
    }

    public void setUserSession(String userId, WebSocketSession session) {
        userSessionMap.put(userId, session);
        sessionMap.put(session.getId(), session);
    }

    public WebSocketSession getUserSession(String userId) {
        return userSessionMap.get(userId);
    }

    public String getUserIdBySession(String sessionId) {
        return sessionToUserMap.get(sessionId);
    }

    public String getLobbyBySession(String sessionId) {
        return sessionToLobbyMap.get(sessionId);
    }

    public WebSocketSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    public Set<String> getSessionsByLobby(String lobbyCode) {
        return lobbySessionsMap.getOrDefault(lobbyCode, new CopyOnWriteArraySet<>());
    }

    public void unregisterSession(String sessionId) {
        String userId = sessionToUserMap.remove(sessionId);
        String lobbyCode = sessionToLobbyMap.remove(sessionId);

        if (userId != null) {
            userSessionMap.remove(userId);
        }

        if (lobbyCode != null) {
            Set<String> sessions = lobbySessionsMap.get(lobbyCode);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    lobbySessionsMap.remove(lobbyCode);
                }
            }
        }

        sessionMap.remove(sessionId);
        log.info("Session {} unregistered", sessionId);
    }
}