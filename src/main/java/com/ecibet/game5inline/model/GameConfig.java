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
    private Integer maxPlayers;
    private Integer trackLength;
    private Integer initialLives;
    private Integer frameRate;
    private Double worldSpeed;
    private Double gravity;
    private Double jumpPower;
    private Double slideHeightReduction;
    private Integer slideDurationMs;
    private Integer jumpDurationMs;

    public static GameConfig defaultConfig() {
        return GameConfig.builder()
                .maxPlayers(5)
                .trackLength(1000)
                .initialLives(3)
                .frameRate(30)
                .worldSpeed(5.0)
                .gravity(0.5)
                .jumpPower(-12.0)
                .slideHeightReduction(0.5)
                .slideDurationMs(500)
                .jumpDurationMs(400)
                .build();
    }
}