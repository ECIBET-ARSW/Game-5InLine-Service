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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyManager {

    private final ActiveLobbiesCache lobbyCache;
    private final LobbyCodeGenerator codeGenerator;
    private final GameEngineManager gameEngineManager;

    public Lobby createLobby(String hostId, String hostName, Integer betAmount, Integer minPlayers, PlayerColor color) {
        if (betAmount == null || !List.of(10, 25, 50).contains(betAmount)) {
            throw new BusinessException(ErrorCode.INVALID_BET_AMOUNT, "Bet amount must be 10, 25, or 50");
        }

        if (minPlayers == null || !List.of(3, 4, 5).contains(minPlayers)) {
            throw new BusinessException(ErrorCode.INVALID_MIN_PLAYERS, "Min players must be 3, 4, or 5");
        }

        String lobbyId = UUID.randomUUID().toString();
        String lobbyCode = generateUniqueCode();

        Lobby lobby = new Lobby(lobbyId, lobbyCode, hostId, betAmount, minPlayers);

        Player host = Player.createNew(hostId, hostName, color, betAmount);
        host.setIsReady(true);
        lobby.addPlayer(host);

        lobbyCache.put(lobby);

        log.info("Lobby created: {} by {} with bet ${}", lobbyCode, hostName, betAmount);

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
            throw new BusinessException(ErrorCode.INVALID_BET_AMOUNT, "Bet amount does not match lobby bet amount");
        }

        Player player = Player.createNew(userId, username, color, betAmount);
        lobby.addPlayer(player);

        lobbyCache.put(lobby);

        log.info("Player {} joined lobby {}", username, lobbyCode);

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
            log.info("Player {} left lobby {}", userId, lobbyCode);
        }
    }

    public void startGame(String lobbyCode, String userId) {
        Lobby lobby = lobbyCache.getByCode(lobbyCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOBBY_NOT_FOUND, "Lobby not found: " + lobbyCode));

        if (!lobby.getHostId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_HOST, "Only the host can start the game");
        }

        if (!lobby.canStart()) {
            throw new BusinessException(ErrorCode.MIN_PLAYERS_NOT_REACHED,
                    "Need at least " + lobby.getMinPlayers() + " players to start. Current: " + lobby.getPlayers().size());
        }

        lobby.setStatus(LobbyStatus.STARTING);
        lobbyCache.put(lobby);

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