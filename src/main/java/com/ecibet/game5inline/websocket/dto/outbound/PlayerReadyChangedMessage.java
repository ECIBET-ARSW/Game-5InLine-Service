package com.ecibet.game5inline.websocket.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerReadyChangedMessage {
    private String type;
    private String playerId;
    private Boolean isReady;

    public static PlayerReadyChangedMessage from(String playerId, Boolean isReady) {
        return PlayerReadyChangedMessage.builder()
                .type("PLAYER_READY_CHANGED")
                .playerId(playerId)
                .isReady(isReady)
                .build();
    }
}