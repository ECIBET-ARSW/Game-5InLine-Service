package com.ecibet.game5inline.model.enums;

public enum ObstacleType {
    WALL,
    SPIKE,
    PIT,
    PLATFORM,
    ROCK,
    LAVA,
    BLADE,
    SINK_BLOCK,
    TRAMPOLINE,
    POWERUP_SPEED;

    public boolean isAnimated() {
        return switch (this) {
            case ROCK, LAVA, BLADE, SINK_BLOCK, POWERUP_SPEED -> true;
            default -> false;
        };
    }

    public int getFrameCount() {
        return switch (this) {
            case SPIKE -> 3;
            case ROCK -> 4;
            case LAVA -> 6;
            case BLADE -> 8;
            case SINK_BLOCK -> 5;
            case POWERUP_SPEED -> 4;
            default -> 1;
        };
    }

    public double getObstacleHeight() {
        return switch (this) {
            case WALL -> 64.0;
            case PIT -> 32.0;
            case PLATFORM -> 16.0;
            case TRAMPOLINE -> 16.0;
            default -> 32.0;
        };
    }

    public double getObstacleWidth() {
        return switch (this) {
            case WALL -> 32.0;
            case PIT -> 64.0;
            case PLATFORM -> 48.0;
            default -> 32.0;
        };
    }
}