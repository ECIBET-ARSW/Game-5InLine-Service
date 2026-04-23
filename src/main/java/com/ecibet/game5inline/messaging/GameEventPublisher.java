package com.ecibet.game5inline.messaging;

import com.ecibet.game5inline.messaging.dto.SettlementEvent;
import com.ecibet.game5inline.model.Lobby;
import com.ecibet.game5inline.model.PlayerResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class GameEventPublisher {

    private static GameEventPublisher instance;
    private RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "game.events";
    private static final String ROUTING_KEY = "game.5inline.settled";

    public GameEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        instance = this;
    }

    public static GameEventPublisher getInstance() {
        return instance;
    }

    public void publishSettlement(Lobby lobby, List<PlayerResult> results, String winnerId, Integer totalPool) {
        List<SettlementEvent.Settlement> settlements = results.stream()
                .map(result -> SettlementEvent.Settlement.builder()
                        .userId(result.getPlayerId())
                        .amount(result.getCoinsEarned() - lobby.getBetAmount())
                        .reason(result.getPosition() == 1 ? "WINNER" : "LOSER")
                        .build())
                .toList();

        SettlementEvent event = SettlementEvent.builder()
                .eventType("game.5inline.settled")
                .gameId(lobby.getId())
                .timestamp(LocalDateTime.now())
                .settlements(settlements)
                .build();

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);

        log.info("Settlement event published for game {}: winner {}, pool {}",
                lobby.getCode(), winnerId, totalPool);
    }
}