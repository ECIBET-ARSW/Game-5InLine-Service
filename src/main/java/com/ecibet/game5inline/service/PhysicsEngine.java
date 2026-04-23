package com.ecibet.game5inline.service;

import com.ecibet.game5inline.model.Player;
import org.springframework.stereotype.Component;

@Component
public class PhysicsEngine {

    private static final double GRAVITY = 0.5;
    private static final double GROUND_Y = 400.0;
    private static final double JUMP_POWER = -12.0;

    public void applyGravity(Player player) {
        if (Boolean.FALSE.equals(player.getIsOnGround())) {
            double newY = player.getY() + GRAVITY;
            player.setY(newY);

            if (player.getY() >= GROUND_Y) {
                player.setY(GROUND_Y);
                player.setIsOnGround(true);
                player.setIsSliding(false);
            }
        }
    }

    public void applyJump(Player player) {
        if (Boolean.TRUE.equals(player.getIsOnGround())) {
            player.setIsOnGround(false);
            player.setLastJumpTime(System.currentTimeMillis());
            player.setY(player.getY() + JUMP_POWER);
        }
    }

    public void applySlide(Player player, int slideDurationMs) {
        if (Boolean.TRUE.equals(player.getIsOnGround())) {
            player.setIsSliding(true);
            player.setSlideEndTime(System.currentTimeMillis() + slideDurationMs);
        }
    }

    public void updateSlide(Player player) {
        if (Boolean.TRUE.equals(player.getIsSliding())) {
            if (System.currentTimeMillis() > player.getSlideEndTime()) {
                player.setIsSliding(false);
            }
        }
    }

    public void updateDistance(Player player, double currentSpeed) {
        player.setDistance(player.getDistance() + currentSpeed / 10.0);
    }
}