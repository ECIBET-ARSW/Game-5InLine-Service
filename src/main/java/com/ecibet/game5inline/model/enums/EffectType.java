package com.ecibet.game5inline.model.enums;

public enum EffectType {
    DUST,
    JUMP,
    EXPLOSION,
    STAR;

    public int getFrameCount() {
        return switch (this) {
            case DUST -> 4;
            case JUMP -> 2;
            case EXPLOSION -> 6;
            case STAR -> 8;
        };
    }
}