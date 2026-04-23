package com.ecibet.game5inline.websocket.dto.outbound;

import com.ecibet.game5inline.model.PlayerResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameEndMessage {
    private String type;
    private List<PlayerResult> results;

    public static GameEndMessage from(List<PlayerResult> results) {
        return GameEndMessage.builder()
                .type("GAME_END")
                .results(results)
                .build();
    }
}