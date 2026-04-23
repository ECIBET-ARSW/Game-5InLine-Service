package com.ecibet.game5inline.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementEvent {
    private String eventType;
    private String gameId;
    private LocalDateTime timestamp;
    private List<Settlement> settlements;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settlement {
        private String userId;
        private Integer amount;
        private String reason;
    }
}