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
    private double gravity = 1.2;

    @Builder.Default
    private double jumpPower = -15.0;

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

    @Builder.Default
    private double groundY = 448.0;

    @Builder.Default
    private double lowHeightY = 430.0;

    @Builder.Default
    private double mediumHeightY = 410.0;

    @Builder.Default
    private double highHeightY = 390.0;

    @Builder.Default
    private double platformY = 400.0;

    @Builder.Default
    private double platformHighY = 370.0;

    public static GameConfig defaultConfig() {
        return GameConfig.builder()
                .worldSpeed(6.0)
                .gravity(1.2)
                .jumpPower(-15.0)
                .slideDurationMs(500.0)
                .initialLives(3)
                .trackLength(10000.0)
                .speedIncreaseInterval(30000.0)
                .speedIncreaseFactor(1.02)
                .maxSpeed(12.0)
                .minSpeed(6.0)
                .groundY(448.0)
                .lowHeightY(430.0)
                .mediumHeightY(410.0)
                .highHeightY(390.0)
                .platformY(400.0)
                .platformHighY(370.0)
                .build();
    }

    public double getWorldSpeed() { return worldSpeed; }
    public double getGravity() { return gravity; }
    public double getJumpPower() { return jumpPower; }
    public double getSlideDurationMs() { return slideDurationMs; }
    public int getInitialLives() { return initialLives; }
    public double getTrackLength() { return trackLength; }
    public double getSpeedIncreaseInterval() { return speedIncreaseInterval; }
    public double getSpeedIncreaseFactor() { return speedIncreaseFactor; }
    public double getMaxSpeed() { return maxSpeed; }
    public double getMinSpeed() { return minSpeed; }
    public double getGroundY() { return groundY; }
    public double getLowHeightY() { return lowHeightY; }
    public double getMediumHeightY() { return mediumHeightY; }
    public double getHighHeightY() { return highHeightY; }
    public double getPlatformY() { return platformY; }
    public double getPlatformHighY() { return platformHighY; }
}