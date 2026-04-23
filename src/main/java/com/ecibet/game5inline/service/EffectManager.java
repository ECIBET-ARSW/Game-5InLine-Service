package com.ecibet.game5inline.service;

import com.ecibet.game5inline.model.Effect;
import com.ecibet.game5inline.model.enums.EffectType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EffectManager {

    private final List<Effect> activeEffects = new ArrayList<>();

    public void spawnDust(double x, double y) {
        activeEffects.add(new Effect(EffectType.DUST, x, y));
    }

    public void spawnJumpEffect(double x, double y) {
        activeEffects.add(new Effect(EffectType.JUMP, x, y));
    }

    public void spawnExplosion(double x, double y) {
        activeEffects.add(new Effect(EffectType.EXPLOSION, x, y));
    }

    public void spawnStar(double x, double y) {
        activeEffects.add(new Effect(EffectType.STAR, x, y));
    }

    public List<Effect> getActiveEffects() {
        return new ArrayList<>(activeEffects);
    }

    public void updateEffects(long now) {
        activeEffects.removeIf(Effect::isExpired);

        for (Effect effect : activeEffects) {
            long elapsed = now - effect.getCreatedAt();
            int frameDuration = 50;
            int frameIndex = (int) (elapsed / frameDuration) % effect.getType().getFrameCount();
            effect.setFrameIndex(frameIndex);
        }
    }

    public void clear() {
        activeEffects.clear();
    }
}