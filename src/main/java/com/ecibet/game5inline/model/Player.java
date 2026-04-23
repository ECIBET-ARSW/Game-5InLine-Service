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
public class Player {
    private String userId;
    private String username;
    private PlayerColor color;
    private Integer betAmount;
    private Boolean isReady;
    private Integer lives;
    private Double distance;
    private Boolean isAlive;
    private Double x;
    private Double y;
    private String animation;
    private Integer frameIndex;
    private Long lastJumpTime;
    private Boolean isSliding;
    private Long slideEndTime;
    private Boolean isOnGround;

    public static Player createNew(String userId, String username, PlayerColor color, Integer betAmount) {
        Player player = new Player();
        player.setUserId(userId);
        player.setUsername(username);
        player.setColor(color);
        player.setBetAmount(betAmount);
        player.setIsReady(false);
        player.setLives(3);
        player.setDistance(0.0);
        player.setIsAlive(true);
        player.setX(100.0);
        player.setY(400.0);
        player.setAnimation("IDLE");
        player.setFrameIndex(0);
        player.setIsSliding(false);
        player.setIsOnGround(true);
        return player;
    }
}