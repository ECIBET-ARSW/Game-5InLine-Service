package com.ecibet.game5inline.websocket.dto.outbound;

import com.ecibet.game5inline.model.Effect;
import com.ecibet.game5inline.model.Obstacle;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.enums.GroundType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateMessage {
    private String type;
    private List<Player> players;
    private List<Obstacle> obstacles;
    private List<Effect> effects;
    private GroundType groundType;
    private Double worldOffset;
    private Long gameTime;

    public static GameStateMessage from(String type, List<Player> players, List<Obstacle> obstacles,
                                        List<Effect> effects, GroundType groundType, Double worldOffset, Long gameTime) {
        return GameStateMessage.builder()
                .type(type)
                .players(players)
                .obstacles(obstacles)
                .effects(effects)
                .groundType(groundType)
                .worldOffset(worldOffset)
                .gameTime(gameTime)
                .build();
    }
}