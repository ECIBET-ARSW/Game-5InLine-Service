package com.ecibet.game5inline.model;

import com.ecibet.game5inline.model.enums.LobbyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lobby {
    private String id;
    private String code;
    private LobbyStatus status;
    private Integer betAmount;
    private Integer minPlayers;
    private Integer maxPlayers;
    private String hostId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private Map<String, Player> players;
    private GameConfig gameConfig;
    private Integer currentTick;

    public Lobby(String id, String code, String hostId, Integer betAmount, Integer minPlayers) {
        this.id = id;
        this.code = code;
        this.hostId = hostId;
        this.betAmount = betAmount;
        this.minPlayers = minPlayers;
        this.maxPlayers = 5;
        this.status = LobbyStatus.WAITING;
        this.createdAt = LocalDateTime.now();
        this.lastActivityAt = LocalDateTime.now();
        this.players = new ConcurrentHashMap<>();
        this.currentTick = 0;
        this.gameConfig = GameConfig.defaultConfig();
    }

    public void addPlayer(Player player) {
        players.put(player.getUserId(), player);
        lastActivityAt = LocalDateTime.now();
    }

    public void removePlayer(String userId) {
        players.remove(userId);
        lastActivityAt = LocalDateTime.now();

        if (players.isEmpty()) {
            status = LobbyStatus.CANCELLED;
        }

        if (hostId.equals(userId) && !players.isEmpty()) {
            hostId = players.keySet().iterator().next();
        }
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public boolean canStart() {
        return players.size() >= minPlayers && LobbyStatus.WAITING.equals(status);
    }

    public boolean allPlayersReady() {
        return players.values().stream().allMatch(p -> Boolean.TRUE.equals(p.getIsReady()));
    }
}