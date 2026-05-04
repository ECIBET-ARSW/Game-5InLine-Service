package com.ecibet.game5inline.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameConfig {

    @Builder.Default
    private double worldSpeed = 6.0;

    @Builder.Default
    private double gravity = 0.9;

    @Builder.Default
    private double jumpPower = -28.0;

    @Builder.Default
    private double slideDurationMs = 500.0;

    @Builder.Default
    private int initialLives = 3;

    @Builder.Default
    private double trackLength = 10000.0;

    @Builder.Default
    private double speedIncreaseInterval = 30000.0;

    @Builder.Default
    private double speedIncreaseFactor = 1.02;

    @Builder.Default
    private double maxSpeed = 12.0;

    @Builder.Default
    private double minSpeed = 6.0;

    public static GameConfig defaultConfig() {
        return GameConfig.builder()
                .worldSpeed(6.0)
                .gravity(0.9)
                .jumpPower(-28.0)
                .slideDurationMs(500.0)
                .initialLives(3)
                .trackLength(10000.0)
                .speedIncreaseInterval(30000.0)
                .speedIncreaseFactor(1.02)
                .maxSpeed(12.0)
                .minSpeed(6.0)
                .build();
    }

    public double getWorldSpeed() {
        return worldSpeed;
    }

    public double getGravity() {
        return gravity;
    }

    public double getJumpPower() {
        return jumpPower;
    }

    public double getSlideDurationMs() {
        return slideDurationMs;
    }

    public int getInitialLives() {
        return initialLives;
    }

    public double getTrackLength() {
        return trackLength;
    }

    public double getSpeedIncreaseInterval() {
        return speedIncreaseInterval;
    }

    public double getSpeedIncreaseFactor() {
        return speedIncreaseFactor;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getMinSpeed() {
        return minSpeed;
    }
}