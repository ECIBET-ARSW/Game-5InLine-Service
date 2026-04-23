package com.ecibet.game5inline.websocket.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerLeftMessage {
    private String type;
    private String playerId;

    public static PlayerLeftMessage from(String playerId) {
        return PlayerLeftMessage.builder()
                .type("PLAYER_LEFT")
                .playerId(playerId)
                .build();
    }
}