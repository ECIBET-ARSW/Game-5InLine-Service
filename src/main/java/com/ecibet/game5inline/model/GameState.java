package com.ecibet.game5inline.model;

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
public class GameState {
    private List<Player> players;
    private List<Obstacle> obstacles;
    private List<Effect> effects;
    private GroundType groundType;
    private Double worldOffset;
    private Long gameTime;
}