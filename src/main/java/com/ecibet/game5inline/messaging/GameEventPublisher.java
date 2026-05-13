package com.ecibet.game5inline.messaging;

import com.ecibet.game5inline.event.BetLostEvent;
import com.ecibet.game5inline.event.BetWonEvent;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.PlayerResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
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
                        .timestamp(new Date().toInstant())
                        .build();

                log.info("Sending BetWonEvent for winner: {} - Amount: {}", userId, winnings);
                try {
                    rabbitTemplate.convertAndSend(GAME_EVENTS_EXCHANGE, BET_WON_ROUTING_KEY, wonEvent);
                } catch (Exception e) {
                    log.error("Error sending BetWonEvent: {}", e.getMessage());
                }

            } else {
                BetLostEvent lostEvent = BetLostEvent.builder()
                        .betId(lobbyId)
                        .userId(userId)
                        .stake(stake)
                        .build();

                log.info("Sending BetLostEvent for loser: {} - Stake: {}", userId, stake);
                try {
                    rabbitTemplate.convertAndSend(GAME_EVENTS_EXCHANGE, BET_LOST_ROUTING_KEY, lostEvent);
                } catch (Exception e) {
                    log.error("Error sending BetLostEvent: {}", e.getMessage());
                }
            }
        }
    }
}