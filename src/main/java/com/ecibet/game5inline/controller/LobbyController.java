package com.ecibet.game5inline.controller;

import com.ecibet.game5inline.exception.BusinessException;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.enums.PlayerColor;
import com.ecibet.game5inline.service.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lobbies")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyManager lobbyManager;

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createLobby(@RequestBody Map<String, Object> request) {
        String userId = (String) request.get("userId");
        String username = (String) request.get("username");
        Integer betAmount = (Integer) request.get("betAmount");
        Integer minPlayers = (Integer) request.get("minPlayers");
        String colorStr = (String) request.get("color");

        PlayerColor color = PlayerColor.valueOf(colorStr.toUpperCase());

        Lobby lobby = lobbyManager.createLobby(userId, username, betAmount, minPlayers, color);

        Map<String, Object> response = new HashMap<>();
        response.put("lobbyCode", lobby.getCode());
        response.put("lobbyId", lobby.getId());
        response.put("betAmount", lobby.getBetAmount());
        response.put("minPlayers", lobby.getMinPlayers());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinLobby(@RequestBody Map<String, Object> request) {
        String lobbyCode = (String) request.get("lobbyCode");
        String userId = (String) request.get("userId");
        String username = (String) request.get("username");
        Integer betAmount = (Integer) request.get("betAmount");
        String colorStr = (String) request.get("color");

        PlayerColor color = PlayerColor.valueOf(colorStr.toUpperCase());

        Lobby lobby = lobbyManager.joinLobby(lobbyCode, userId, username, betAmount, color);

        Map<String, Object> response = new HashMap<>();
        response.put("lobbyCode", lobby.getCode());
        response.put("lobbyId", lobby.getId());
        response.put("betAmount", lobby.getBetAmount());
        response.put("players", lobby.getPlayers().size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/public")
    public ResponseEntity<List<Lobby>> getPublicLobbies() {
        return ResponseEntity.ok(lobbyManager.getPublicLobbies());
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leaveLobby(@RequestBody Map<String, Object> request) {
        String lobbyCode = (String) request.get("lobbyCode");
        String userId = (String) request.get("userId");

        lobbyManager.leaveLobby(lobbyCode, userId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startGame(@RequestBody Map<String, Object> request) {
        String lobbyCode = (String) request.get("lobbyCode");
        String userId = (String) request.get("userId");

        lobbyManager.startGame(lobbyCode, userId);

        return ResponseEntity.ok().build();
    }
}