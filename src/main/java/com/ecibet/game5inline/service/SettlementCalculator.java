package com.ecibet.game5inline.service;

import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.PlayerResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SettlementCalculator {

    public SettlementResult calculate(Lobby lobby, String winnerId, long gameDuration) {
        Integer totalPool = lobby.getPlayers().values().stream()
                .mapToInt(Player::getBetAmount)
                .sum();

        List<PlayerResult> results = new ArrayList<>();
        int position = 1;

        List<Player> sortedPlayers = lobby.getPlayers().values().stream()
                .sorted((a, b) -> Double.compare(b.getDistance(), a.getDistance()))
                .toList();

        for (Player player : sortedPlayers) {
            Integer coinsEarned = player.getUserId().equals(winnerId) ? totalPool : 0;

            PlayerResult result = PlayerResult.builder()
                    .playerId(player.getUserId())
                    .playerName(player.getUsername())
                    .position(position++)
                    .coinsEarned(coinsEarned)
                    .color(player.getColor())
                    .distance(player.getDistance())
                    .timeAlive(gameDuration)
                    .build();
            results.add(result);
        }

        return new SettlementResult(results, winnerId, totalPool);
    }

    public record SettlementResult(List<PlayerResult> results, String winnerId, Integer totalPool) {}
}