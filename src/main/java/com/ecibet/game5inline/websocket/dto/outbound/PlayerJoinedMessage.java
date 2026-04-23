package com.ecibet.game5inline.websocket.dto.outbound;

import com.ecibet.game5inline.model.enums.PlayerColor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerJoinedMessage {
    private String type;
    private String playerId;
    private String playerName;
    private PlayerColor color;
    private Boolean isReady;

    public static PlayerJoinedMessage from(String playerId, String playerName, PlayerColor color, Boolean isReady) {
        return PlayerJoinedMessage.builder()
                .type("PLAYER_JOINED")
                .playerId(playerId)
                .playerName(playerName)
                .color(color)
                .isReady(isReady)
                .build();
    }
}