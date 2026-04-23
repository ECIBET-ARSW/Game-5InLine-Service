package com.ecibet.game5inline.model;

import com.ecibet.game5inline.model.enums.PlayerColor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResult {
    private String playerId;
    private String playerName;
    private Integer position;
    private Integer coinsEarned;
    private PlayerColor color;
    private Double distance;
    private Long timeAlive;
}