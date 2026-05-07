package com.ecibet.game5inline.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetWonEvent {
    private String betId;
    private String userId;
    private BigDecimal amount;
    private BigDecimal stake;
    private BigDecimal odds;
    private String selectionName;
    private String eventId;
    private String transactionType;
    private Instant timestamp;
}