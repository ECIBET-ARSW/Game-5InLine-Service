package com.ecibet.game5inline.messaging;

import com.ecibet.game5inline.event.BetLostEvent;
import com.ecibet.game5inline.event.BetWonEvent;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.PlayerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.ecibet.game5inline.config.RabbitMQConfig.BET_LOST_ROUTING_KEY;
import static com.ecibet.game5inline.config.RabbitMQConfig.BET_WON_ROUTING_KEY;
import static com.ecibet.game5inline.config.RabbitMQConfig.GAME_EVENTS_EXCHANGE;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishSettlement(Lobby lobby, List<PlayerResult> results, String winnerId, Integer totalPool) {
        String lobbyCode = lobby.getCode();
        String lobbyId = lobby.getId().toString();

        log.info("Publishing settlement for lobby {} - Winner: {}, Total Pool: {}", lobbyCode, winnerId, totalPool);

        for (PlayerResult result : results) {
            String userId = result.getPlayerId();
            BigDecimal stake = BigDecimal.valueOf(lobby.getBetAmount());

            if (userId.equals(winnerId)) {
                BigDecimal winnings = BigDecimal.valueOf(totalPool);

                BetWonEvent wonEvent = BetWonEvent.builder()
                        .betId(lobbyId)
                        .userId(userId)
                        .amount(winnings)
                        .stake(stake)
                        .odds(BigDecimal.valueOf(1.0))
                        .selectionName("5InLine Race Winner")
                        .eventId(lobbyCode)
                        .transactionType("GAME_WON")
                        .timestamp(Instant.now())
                        .build();

                log.info("Sending BetWonEvent for winner: {} - Amount: {}", userId, winnings);
                rabbitTemplate.convertAndSend(GAME_EVENTS_EXCHANGE, BET_WON_ROUTING_KEY, wonEvent);

            } else {
                BetLostEvent lostEvent = BetLostEvent.builder()
                        .betId(lobbyId)
                        .userId(userId)
                        .stake(stake)
                        .build();

                log.info("Sending BetLostEvent for loser: {} - Stake: {}", userId, stake);
                rabbitTemplate.convertAndSend(GAME_EVENTS_EXCHANGE, BET_LOST_ROUTING_KEY, lostEvent);
            }
        }
    }

    public void publishSingleBetWon(String lobbyId, String userId, BigDecimal stake, BigDecimal winnings, String lobbyCode) {
        BetWonEvent event = BetWonEvent.builder()
                .betId(lobbyId)
                .userId(userId)
                .amount(winnings)
                .stake(stake)
                .odds(BigDecimal.valueOf(1.0))
                .selectionName("5InLine Race Winner")
                .eventId(lobbyCode)
                .transactionType("GAME_WON")
                .timestamp(Instant.now())
                .build();

        rabbitTemplate.convertAndSend(GAME_EVENTS_EXCHANGE, BET_WON_ROUTING_KEY, event);
    }

    public void publishSingleBetLost(String lobbyId, String userId, BigDecimal stake) {
        BetLostEvent event = BetLostEvent.builder()
                .betId(lobbyId)
                .userId(userId)
                .stake(stake)
                .build();

        rabbitTemplate.convertAndSend(GAME_EVENTS_EXCHANGE, BET_LOST_ROUTING_KEY, event);
    }
}