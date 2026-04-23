package com.ecibet.game5inline.model;

import com.ecibet.game5inline.model.enums.EffectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Effect {
    private EffectType type;
    private Integer frameIndex;
    private Double x;
    private Double y;
    private Long duration;
    private Long createdAt;

    public Effect(EffectType type, Double x, Double y) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.frameIndex = 0;
        this.createdAt = System.currentTimeMillis();

        switch (type) {
            case EXPLOSION:
                this.duration = 500L;
                break;
            case STAR:
                this.duration = 800L;
                break;
            default:
                this.duration = 200L;
                break;
        }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > duration;
    }
}