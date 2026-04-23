package com.ecibet.game5inline.model.enums;

public enum AnimationType {
    IDLE,
    RUNNING,
    JUMPING,
    SLIDING,
    DYING,
    CELEBRATING;

    public int getFrameCount() {
        return switch (this) {
            case IDLE -> 3;
            case RUNNING -> 6;
            case JUMPING -> 4;
            case SLIDING -> 2;
            case DYING -> 5;
            case CELEBRATING -> 4;
        };
    }
}