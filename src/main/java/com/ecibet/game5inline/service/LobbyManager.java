package com.ecibet.game5inline.service;

import com.ecibet.game5inline.cache.ActiveLobbiesCache;
import com.ecibet.game5inline.exception.BusinessException;
import com.ecibet.game5inline.exception.ErrorCode;
import com.ecibet.game5inline.model.GameConfig;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.enums.LobbyStatus;
import com.ecibet.game5inline.model.enums.PlayerColor;
import com.ecibet.game5inline.util.LobbyCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyManager {

    private final ActiveLobbiesCache lobbyCache;
    private final LobbyCodeGenerator codeGenerator;
    private final GameEngineManager gameEngineManager;
    private final ApplicationEventPublisher eventPublisher;

    private static final List<Integer> VALID_BET_AMOUNTS = Arrays.asList(1000, 5000, 10000, 25000, 50000, 100000, 200000, 500000);
    private static final List<Integer> VALID_MIN_PLAYERS = Arrays.asList(3, 4, 5);

    private void publishLobbyEvent(String lobbyCode, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("lobbyCode", lobbyCode);
        eventPublisher.publishEvent(event);
        log.info("Evento publicado: {} para lobby {}", eventType, lobbyCode);
    }

    public Lobby createLobby(String hostId, String hostName, Integer betAmount, Integer minPlayers, PlayerColor color) {
        if (betAmount == null || !VALID_BET_AMOUNTS.contains(betAmount)) {
            throw new BusinessException(ErrorCode.INVALID_BET_AMOUNT,
                    "Bet amount must be one of: " + VALID_BET_AMOUNTS);
        }

        if (minPlayers == null || !VALID_MIN_PLAYERS.contains(minPlayers)) {
            throw new BusinessException(ErrorCode.INVALID_MIN_PLAYERS,
                    "Min players must be 3, 4, or 5");
        }

        String lobbyId = UUID.randomUUID().toString();
        String lobbyCode = generateUniqueCode();

        Lobby lobby = new Lobby(lobbyId, lobbyCode, hostId, betAmount, minPlayers);

        Player host = Player.createNew(hostId, hostName, color, betAmount);
        host.setIsReady(true);
        lobby.addPlayer(host);

        lobbyCache.put(lobby);

        log.info("Lobby created: {} by {} with bet ${} COP", lobbyCode, hostName, betAmount);

        publishLobbyEvent(lobbyCode, "LOBBY_UPDATED");

        return lobby;
    }

    public Lobby joinLobby(String lobbyCode, String userId, String username, Integer betAmount, PlayerColor color) {
        Lobby lobby = lobbyCache.getByCode(lobbyCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found: " + lobbyCode));

        if (lobby.getStatus() != LobbyStatus.WAITING) {
            throw new BusinessException(ErrorCode.LOBBY_ALREADY_STARTED, "Lobby already started or finished");
        }

        if (lobby.isFull()) {
            throw new BusinessException(ErrorCode.LOBBY_FULL, "Lobby is full");
        }

        if (lobby.getPlayers().containsKey(userId)) {
            throw new BusinessException(ErrorCode.PLAYER_ALREADY_IN_LOBBY, "Player already in lobby");
        }

        boolean colorTaken = lobby.getPlayers().values().stream()
                .anyMatch(p -> p.getColor() == color);

        if (colorTaken) {
            throw new BusinessException(ErrorCode.COLOR_ALREADY_TAKEN, "Color already taken: " + color);
        }

        if (!betAmount.equals(lobby.getBetAmount())) {
            throw new BusinessException(ErrorCode.INVALID_BET_AMOUNT,
                    "Bet amount does not match lobby bet amount. Expected: " + lobby.getBetAmount() + ", got: " + betAmount);
        }

        Player player = Player.createNew(userId, username, color, betAmount);
        lobby.addPlayer(player);

        lobbyCache.put(lobby);

        log.info("Player {} joined lobby {}", username, lobbyCode);

        publishLobbyEvent(lobbyCode, "LOBBY_UPDATED");

        return lobby;
    }

    public void leaveLobby(String lobbyCode, String userId) {
        Lobby lobby = lobbyCache.getByCode(lobbyCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found: " + lobbyCode));

        if (lobby.getStatus() == LobbyStatus.IN_PROGRESS) {
            log.warn("Player {} tried to leave lobby {} while game in progress", userId, lobbyCode);
            return;
        }

        lobby.removePlayer(userId);

        if (lobby.getPlayers().isEmpty()) {
            lobbyCache.remove(lobby.getId());
            log.info("Lobby {} removed (empty)", lobbyCode);
        } else {
            lobbyCache.put(lobby);
            publishLobbyEvent(lobbyCode, "LOBBY_UPDATED");
            log.info("Player {} left lobby {}", userId, lobbyCode);
        }
    }

    public void toggleReady(String lobbyCode, String userId) {
        Lobby lobby = lobbyCache.getByCode(lobbyCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found: " + lobbyCode));

        Player player = lobby.getPlayers().get(userId);
        if (player != null) {
            player.setIsReady(!player.getIsReady());
            lobbyCache.put(lobby);

            publishLobbyEvent(lobbyCode, "LOBBY_UPDATED");

            log.info("Player {} ready status: {} in lobby {}", userId, player.getIsReady(), lobbyCode);
        }
    }

    public void startGame(String lobbyCode, String userId) {
        Lobby lobby = lobbyCache.getByCode(lobbyCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found: " + lobbyCode));

        log.info("StartGame called - lobby: {}, userId: {}, hostId: {}", lobbyCode, userId, lobby.getHostId());

        if (!lobby.getHostId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_HOST, "Only the host can start the game");
        }

        log.info("Players in lobby: {}, Min players required: {}", lobby.getPlayers().size(), lobby.getMinPlayers());

        if (lobby.getPlayers().size() < lobby.getMinPlayers()) {
            throw new BusinessException(ErrorCode.MIN_PLAYERS_NOT_REACHED,
                    "Need at least " + lobby.getMinPlayers() + " players to start. Current: " + lobby.getPlayers().size());
        }

        boolean allReady = lobby.allPlayersReady();
        log.info("All players ready: {}", allReady);

        for (Player player : lobby.getPlayers().values()) {
            log.info("Player {} - ready: {}", player.getUsername(), player.getIsReady());
        }

        if (!allReady) {
            throw new BusinessException(ErrorCode.MIN_PLAYERS_NOT_REACHED,
                    "All players must be ready before starting the game");
        }

        lobby.setStatus(LobbyStatus.IN_PROGRESS);
        lobbyCache.put(lobby);

        publishLobbyEvent(lobbyCode, "GAME_STARTING");

        GameConfig config = lobby.getGameConfig();
        config.setWorldSpeed(5.0);

        gameEngineManager.startEngine(lobby, config);

        log.info("Game started in lobby {} by host {}", lobbyCode, userId);
    }

    public List<Lobby> getPublicLobbies() {
        return lobbyCache.getAll().stream()
                .filter(lobby -> lobby.getStatus() == LobbyStatus.WAITING)
                .filter(lobby -> lobby.getPlayers().size() < lobby.getMaxPlayers())
                .toList();
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = codeGenerator.generate();
        } while (lobbyCache.existsByCode(code));
        return code;
    }
}