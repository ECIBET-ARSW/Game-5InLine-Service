package com.ecibet.game5inline.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetLostEvent {
    private String betId;
    private String userId;
    private BigDecimal stake;
}