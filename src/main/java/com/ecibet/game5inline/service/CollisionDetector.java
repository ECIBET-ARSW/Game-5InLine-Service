package com.ecibet.game5inline.service;

import com.ecibet.game5inline.model.Obstacle;
import com.ecibet.game5inline.model.Player;
import com.ecibet.game5inline.model.enums.ObstacleType;
import org.springframework.stereotype.Component;

@Component
public class CollisionDetector {

    private static final double PLAYER_WIDTH = 30.0;
    private static final double PLAYER_HEIGHT = 30.0;
    private static final double SLIDE_HEIGHT_REDUCTION = 0.5;

    public boolean checkCollision(Player player, Obstacle obstacle, double worldOffset) {
        double playerLeft = player.getX();
        double playerRight = player.getX() + PLAYER_WIDTH;
        double playerTop = player.getY();
        double playerBottom = player.getY() + PLAYER_HEIGHT;

        if (Boolean.TRUE.equals(player.getIsSliding())) {
            playerTop = player.getY() + (PLAYER_HEIGHT * SLIDE_HEIGHT_REDUCTION);
        }

        double obstacleLeft = obstacle.getX() - worldOffset;
        double obstacleRight = obstacle.getX() - worldOffset + obstacle.getWidth();
        double obstacleTop = obstacle.getY();
        double obstacleBottom = obstacle.getY() + obstacle.getHeight();

        return playerRight > obstacleLeft && playerLeft < obstacleRight &&
                playerBottom > obstacleTop && playerTop < obstacleBottom;
    }

    public CollisionResult getCollisionResult(ObstacleType type) {
        return switch (type) {
            case SPIKE, LAVA, BLADE -> CollisionResult.INSTANT_DEATH;
            case WALL -> CollisionResult.BLOCK;
            case TRAMPOLINE -> CollisionResult.BOUNCE;
            case POWERUP_SPEED -> CollisionResult.POWERUP;
            case PIT -> CollisionResult.FALL;
            default -> CollisionResult.DAMAGE;
        };
    }

    public enum CollisionResult {
        INSTANT_DEATH,
        DAMAGE,
        BLOCK,
        BOUNCE,
        POWERUP,
        FALL
    }
}