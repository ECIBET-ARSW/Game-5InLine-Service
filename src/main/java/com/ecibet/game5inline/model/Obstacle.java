package com.ecibet.game5inline.model;

import com.ecibet.game5inline.model.enums.ObstacleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Obstacle {
    private ObstacleType type;
    private Integer frameIndex;
    private Double x;
    private Double y;
    private Double width;
    private Double height;

    public Obstacle(ObstacleType type, Double x, Double y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.frameIndex = 0;
        this.width = 32.0;
        this.height = 32.0;

        switch (type) {
            case WALL:
                this.height = 64.0;
                break;
            case PIT:
                this.width = 64.0;
                this.height = 32.0;
                break;
            case PLATFORM:
                this.width = 48.0;
                this.height = 16.0;
                break;
            case TRAMPOLINE:
                this.width = 32.0;
                this.height = 16.0;
                break;
            default:
                break;
        }
    }
}