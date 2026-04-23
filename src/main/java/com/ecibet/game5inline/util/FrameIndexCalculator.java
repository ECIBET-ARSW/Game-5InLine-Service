package com.ecibet.game5inline.util;

import com.ecibet.game5inline.model.enums.AnimationType;
import org.springframework.stereotype.Component;

@Component
public class FrameIndexCalculator {

    private static final int TICKS_PER_SECOND = 30;

    public int calculateFrameIndex(AnimationType animation, long animationStartTime, int totalFrames) {
        if (totalFrames <= 1) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - animationStartTime;

        int frameDuration = getFrameDurationMs(animation);
        int frameIndex = (int) (elapsed / frameDuration) % totalFrames;

        return frameIndex;
    }

    private int getFrameDurationMs(AnimationType animation) {
        return switch (animation) {
            case IDLE -> 300;
            case RUNNING -> 50;
            case JUMPING -> 100;
            case SLIDING -> 100;
            case DYING -> 100;
            case CELEBRATING -> 100;
        };
    }
}