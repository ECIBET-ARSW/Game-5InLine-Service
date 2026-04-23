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
public class PlayerColorChangedMessage {
    private String type;
    private String playerId;
    private PlayerColor color;

    public static PlayerColorChangedMessage from(String playerId, PlayerColor color) {
        return PlayerColorChangedMessage.builder()
                .type("PLAYER_COLOR_CHANGED")
                .playerId(playerId)
                .color(color)
                .build();
    }
}